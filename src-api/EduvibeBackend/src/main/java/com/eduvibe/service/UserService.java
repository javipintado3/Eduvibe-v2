package com.eduvibe.service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.eduvibe.dto.common.PageResponse;
import com.eduvibe.dto.user.CreateUserRequest;
import com.eduvibe.dto.user.CreateUserResponse;
import com.eduvibe.dto.user.ImportUsersResponse;
import com.eduvibe.dto.user.ImportUsersResponse.FilaImportada;
import com.eduvibe.dto.user.InvitationResponse;
import com.eduvibe.dto.user.UpdateUserRequest;
import com.eduvibe.dto.user.UserClassResponse;
import com.eduvibe.dto.user.UserResponse;
import com.eduvibe.exception.ApiException;
import com.eduvibe.exception.BadRequestException;
import com.eduvibe.exception.ConflictException;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.model.Organization;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.UserRole;
import com.eduvibe.model.enums.UserStatus;
import com.eduvibe.repository.EnrollmentRepository;
import com.eduvibe.repository.OrganizationRepository;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.repository.spec.UserSpecifications;
import com.eduvibe.security.AuthenticatedUser;
import com.eduvibe.util.Csv;

import lombok.RequiredArgsConstructor;

/**
 * Gestión de usuarios desde el panel de administración.
 *
 * Todas las operaciones quedan acotadas a la organización de quien las pide:
 * un administrador no puede ver ni tocar cuentas de otro centro aunque conozca
 * su identificador.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final InvitationService invitationService;
    private final AuthService authService;

    /**
     * Da de alta una cuenta en estado pendiente y emite su invitación.
     *
     * La cuenta nace sin contraseña a propósito: la establece la persona al
     * aceptar la invitación, de modo que nunca existe una contraseña que el
     * administrador conozca.
     */
    @Transactional
    public CreateUserResponse crear(CreateUserRequest peticion) {
        AuthenticatedUser admin = authService.identidadActual();
        Organization organizacion = organizacionDe(admin);

        User usuario = crearCuenta(organizacion, peticion.name(), peticion.email(), peticion.role());
        InvitationResponse invitacion = invitationService.emitirPara(usuario);

        return new CreateUserResponse(UserResponse.de(usuario), invitacion);
    }

    /**
     * Crea la cuenta en estado pendiente, sin emitir todavía la invitación.
     *
     * Lo comparten el alta individual y cada fila de una importación CSV: las
     * mismas reglas (email único, dominio permitido, rol válido) tienen que
     * cumplirse en los dos sitios, y solo viven aquí una vez.
     */
    User crearCuenta(Organization organizacion, String name, String email, String role) {
        String emailNormalizado = User.normalizarEmail(email);

        if (userRepository.existsByEmail(emailNormalizado)) {
            throw new ConflictException("Ya existe una cuenta con el email " + emailNormalizado);
        }
        if (!organizacion.admiteEmail(emailNormalizado)) {
            throw new BadRequestException(
                    "El email debe pertenecer al dominio " + organizacion.getAllowedDomain());
        }

        User usuario = new User(organizacion, emailNormalizado, name.trim(), UserRole.desdeValor(role));
        // saveAndFlush y no save: el INSERT tiene que ejecutarse ya para que
        // lleguen los valores que pone la base de datos (created_at), que si no
        // viajarían a null en la respuesta.
        userRepository.saveAndFlush(usuario);
        return usuario;
    }

    /**
     * Listado con filtros opcionales. Los criterios se componen con
     * Specifications, de forma que el filtro que no se indica ni siquiera
     * aparece en la consulta.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listar(String role, String status, String busqueda, UUID excludeClassId,
                                             Pageable pageable) {
        AuthenticatedUser admin = authService.identidadActual();

        Specification<User> filtro = Specification
                .where(UserSpecifications.deOrganizacion(admin.organizationId()))
                .and(UserSpecifications.conRol(rolONulo(role)))
                .and(UserSpecifications.conEstado(estadoONulo(status)))
                .and(UserSpecifications.queContenga(busqueda))
                .and(UserSpecifications.noMatriculadoEn(excludeClassId));

        Page<User> pagina = userRepository.findAll(filtro, pageable);

        return PageResponse.de(pagina, UserResponse::de);
    }

    private UserRole rolONulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : UserRole.desdeValor(valor);
    }

    private UserStatus estadoONulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : UserStatus.desdeValor(valor);
    }

    @Transactional(readOnly = true)
    public UserResponse obtener(UUID id) {
        return UserResponse.de(buscarEnMiOrganizacion(id));
    }

    /**
     * Edita nombre, email y rol de una cuenta ya existente.
     *
     * No toca la contraseña ni el estado: tienen sus propios flujos (la
     * persona la establece al aceptar su invitación; activar/desactivar es
     * {@link #cambiarEstado}).
     */
    @Transactional
    public UserResponse actualizar(UUID id, UpdateUserRequest peticion) {
        User usuario = buscarEnMiOrganizacion(id);
        String emailNormalizado = User.normalizarEmail(peticion.email());

        if (userRepository.existsByEmailAndIdNot(emailNormalizado, id)) {
            throw new ConflictException("Ya existe una cuenta con el email " + emailNormalizado);
        }
        if (!usuario.getOrganization().admiteEmail(emailNormalizado)) {
            throw new BadRequestException(
                    "El email debe pertenecer al dominio " + usuario.getOrganization().getAllowedDomain());
        }

        usuario.setName(peticion.name().trim());
        usuario.setEmail(emailNormalizado);
        usuario.setRole(UserRole.desdeValor(peticion.role()));
        userRepository.save(usuario);

        return UserResponse.de(usuario);
    }

    /** Las clases en las que participa, para su ficha en el panel de administración. */
    @Transactional(readOnly = true)
    public List<UserClassResponse> clasesDe(UUID id) {
        buscarEnMiOrganizacion(id);

        return enrollmentRepository.findByUserIdOrderBySchoolClassNameAsc(id).stream()
                .map(UserClassResponse::de)
                .toList();
    }

    /**
     * Activa o desactiva una cuenta.
     *
     * No hay borrado: desactivar conserva el histórico académico, que es lo que
     * se quiere en un centro educativo. Y un administrador no puede
     * desactivarse a sí mismo, o podría dejar la organización sin nadie capaz
     * de volver a entrar.
     */
    @Transactional
    public UserResponse cambiarEstado(UUID id, String nuevoEstado) {
        AuthenticatedUser admin = authService.identidadActual();

        if (admin.id().equals(id)) {
            throw new BadRequestException("No puedes cambiar el estado de tu propia cuenta");
        }

        User usuario = buscarEnMiOrganizacion(id);
        UserStatus estado = UserStatus.desdeValor(nuevoEstado);

        if (estado == UserStatus.ACTIVE && usuario.getPasswordHash() == null) {
            throw new BadRequestException(
                    "Esta cuenta aún no ha aceptado su invitación, así que no se puede activar. "
                            + "Vuelve a enviarle la invitación.");
        }

        usuario.setStatus(estado);
        userRepository.save(usuario);

        return UserResponse.de(usuario);
    }

    /**
     * Vuelve a emitir la invitación de una cuenta que todavía no la ha
     * aceptado. No se crea un usuario nuevo: se renueva el enlace del que ya
     * existe.
     */
    @Transactional
    public InvitationResponse reenviarInvitacion(UUID id) {
        User usuario = buscarEnMiOrganizacion(id);

        if (usuario.getStatus() != UserStatus.PENDING) {
            throw new ConflictException("Esta cuenta ya está activada; no procede reenviar la invitación");
        }

        return invitationService.emitirPara(usuario);
    }

    /**
     * Exporta el listado (con los mismos filtros que {@link #listar}) en CSV,
     * sin paginar: quien exporta quiere el conjunto completo, no una página.
     */
    @Transactional(readOnly = true)
    public String exportarCsv(String role, String status, String busqueda) {
        AuthenticatedUser admin = authService.identidadActual();

        Specification<User> filtro = Specification
                .where(UserSpecifications.deOrganizacion(admin.organizationId()))
                .and(UserSpecifications.conRol(rolONulo(role)))
                .and(UserSpecifications.conEstado(estadoONulo(status)))
                .and(UserSpecifications.queContenga(busqueda));

        List<User> usuarios = userRepository.findAll(filtro, Sort.by("name"));

        StringBuilder salida = new StringBuilder();
        salida.append(Csv.fila("name", "email", "role", "status", "createdAt")).append('\n');
        for (User usuario : usuarios) {
            String creado = usuario.getCreatedAt() == null ? "" : usuario.getCreatedAt().toString();
            salida.append(Csv.fila(usuario.getName(), usuario.getEmail(), usuario.getRole().getValor(),
                    usuario.getStatus().getValor(), creado)).append('\n');
        }
        return salida.toString();
    }

    /**
     * Alta masiva desde un CSV con columnas {@code name}, {@code email},
     * {@code role} (cabecera obligatoria, en cualquier orden).
     *
     * Cada fila se intenta por separado con {@link #crearCuenta}, la misma
     * regla que el alta individual: un email duplicado o un rol inválido en
     * una fila no impide que se den de alta las demás. El resultado dice
     * fila a fila qué se creó y qué no, para corregir solo lo que falló.
     */
    @Transactional
    public ImportUsersResponse importar(MultipartFile file) {
        AuthenticatedUser admin = authService.identidadActual();
        Organization organizacion = organizacionDe(admin);

        List<List<String>> lineas;
        try (Reader lector = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            lineas = Csv.leer(lector);
        } catch (IOException e) {
            throw new BadRequestException("No se ha podido leer el fichero: " + e.getMessage());
        }

        if (lineas.isEmpty()) {
            throw new BadRequestException("El fichero está vacío");
        }

        Map<String, Integer> columna = indiceDeColumnas(lineas.get(0));
        if (!columna.keySet().containsAll(List.of("name", "email", "role"))) {
            throw new BadRequestException("La cabecera del CSV debe tener las columnas name, email y role");
        }

        List<FilaImportada> filas = new ArrayList<>();
        int creados = 0;

        for (int i = 1; i < lineas.size(); i++) {
            List<String> fila = lineas.get(i);
            int numeroFila = i + 1; // +1: la cabecera es la fila 1 del fichero
            String email = valorDe(fila, columna.get("email"));

            try {
                String name = valorDe(fila, columna.get("name"));
                String role = valorDe(fila, columna.get("role"));

                if (name.isBlank() || email.isBlank() || role.isBlank()) {
                    throw new BadRequestException("Faltan columnas: se necesitan name, email y role");
                }

                User usuario = crearCuenta(organizacion, name, email, role);
                invitationService.emitirPara(usuario);
                creados++;
                filas.add(new FilaImportada(numeroFila, email, true, null));
            } catch (ApiException | IllegalArgumentException e) {
                filas.add(new FilaImportada(numeroFila, email, false, e.getMessage()));
            }
        }

        return new ImportUsersResponse(filas.size(), creados, filas);
    }

    /** Índice de cada columna con el nombre de la cabecera en minúsculas, para admitir cualquier orden. */
    private Map<String, Integer> indiceDeColumnas(List<String> cabecera) {
        Map<String, Integer> indice = new HashMap<>();
        for (int i = 0; i < cabecera.size(); i++) {
            indice.put(cabecera.get(i).trim().toLowerCase(), i);
        }
        return indice;
    }

    private String valorDe(List<String> fila, Integer indice) {
        return indice != null && indice < fila.size() ? fila.get(indice) : "";
    }

    private User buscarEnMiOrganizacion(UUID id) {
        AuthenticatedUser admin = authService.identidadActual();

        return userRepository.findById(id)
                .filter(u -> u.getOrganization().getId().equals(admin.organizationId()))
                // Un usuario de otra organización se trata como inexistente:
                // responder 403 confirmaría que ese identificador existe.
                .orElseThrow(() -> NotFoundException.de("Usuario", id));
    }

    private Organization organizacionDe(AuthenticatedUser admin) {
        return organizationRepository.findById(admin.organizationId())
                .orElseThrow(() -> NotFoundException.de("Organización", admin.organizationId()));
    }
}
