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
import com.eduvibe.dto.user.ImportUsersResponse.ImportedRow;
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
    private final PersonalDataService personalDataService;
    private final ProtectedDemoAccounts protectedDemoAccounts;

    /**
     * Da de alta una cuenta en estado pendiente y emite su invitación.
     *
     * La cuenta nace sin contraseña a propósito: la establece la persona al
     * aceptar la invitación, de modo que nunca existe una contraseña que el
     * administrador conozca.
     */
    @Transactional
    public CreateUserResponse create(CreateUserRequest request) {
        AuthenticatedUser admin = authService.currentIdentity();
        Organization organization = organizationOf(admin);

        User user = createAccount(organization, request.name(), request.email(), request.role());
        InvitationResponse invitation = invitationService.issueFor(user);

        return new CreateUserResponse(UserResponse.of(user), invitation);
    }

    /**
     * Crea la cuenta en estado pendiente, sin emitir todavía la invitación.
     *
     * Lo comparten el alta individual y cada fila de una importación CSV: las
     * mismas reglas (email único, dominio permitido, rol válido) tienen que
     * cumplirse en los dos sitios, y solo viven aquí una vez.
     */
    User createAccount(Organization organization, String name, String email, String role) {
        String emailNormalized = User.normalizeEmail(email);

        if (userRepository.existsByEmail(emailNormalized)) {
            throw new ConflictException("Ya existe una cuenta con el email " + emailNormalized);
        }
        if (!organization.acceptsEmail(emailNormalized)) {
            throw new BadRequestException(
                    "El email debe pertenecer al dominio " + organization.getAllowedDomain());
        }

        User user = new User(organization, emailNormalized, name.trim(), UserRole.fromValue(role));
        // saveAndFlush y no save: el INSERT tiene que ejecutarse ya para que
        // lleguen los valores que pone la base de datos (created_at), que si no
        // viajarían a null en la respuesta.
        userRepository.saveAndFlush(user);
        return user;
    }

    /**
     * Listado con filtros opcionales. Los criterios se componen con
     * Specifications, de forma que el filtro que no se indica ni siquiera
     * aparece en la consulta.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String role, String status, String search, UUID excludeClassId,
                                             Pageable pageable) {
        AuthenticatedUser admin = authService.currentIdentity();

        Specification<User> filter = Specification
                .where(UserSpecifications.ofOrganization(admin.organizationId()))
                .and(UserSpecifications.notDeleted())
                .and(UserSpecifications.withRole(roleOrNull(role)))
                .and(UserSpecifications.withStatus(statusOrNull(status)))
                .and(UserSpecifications.thatContains(search))
                .and(UserSpecifications.notEnrolledIn(excludeClassId));

        Page<User> pagina = userRepository.findAll(filter, pageable);

        return PageResponse.of(pagina, UserResponse::of);
    }

    private UserRole roleOrNull(String value) {
        return (value == null || value.isBlank()) ? null : UserRole.fromValue(value);
    }

    private UserStatus statusOrNull(String value) {
        return (value == null || value.isBlank()) ? null : UserStatus.fromValue(value);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return UserResponse.of(findInMyOrganization(id));
    }

    /**
     * Edita nombre, email y rol de una cuenta ya existente.
     *
     * No toca la contraseña ni el estado: tienen sus propios flujos (la
     * persona la establece al aceptar su invitación; activar/desactivar es
     * {@link #changeStatus}).
     */
    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = findInMyOrganization(id);
        protectedDemoAccounts.requireNotProtected(user);
        String emailNormalized = User.normalizeEmail(request.email());

        if (userRepository.existsByEmailAndIdNot(emailNormalized, id)) {
            throw new ConflictException("Ya existe una cuenta con el email " + emailNormalized);
        }
        if (!user.getOrganization().acceptsEmail(emailNormalized)) {
            throw new BadRequestException(
                    "El email debe pertenecer al dominio " + user.getOrganization().getAllowedDomain());
        }

        user.setName(request.name().trim());
        user.setEmail(emailNormalized);
        user.setRole(UserRole.fromValue(request.role()));
        userRepository.save(user);

        return UserResponse.of(user);
    }

    /** Las clases en las que participa, para su ficha en el panel de administración. */
    @Transactional(readOnly = true)
    public List<UserClassResponse> schoolClassesOf(UUID id) {
        findInMyOrganization(id);

        return enrollmentRepository.findByUserIdOrderBySchoolClassNameAsc(id).stream()
                .map(UserClassResponse::of)
                .toList();
    }

    /**
     * Activa o desactiva una cuenta.
     *
     * Desactivar es reversible y conserva todo el histórico académico, que es lo que
     * se quiere en un centro educativo; para quitar a alguien del centro está
     * {@link #delete}. Un administrador no puede
     * desactivarse a sí mismo, o podría dejar la organización sin nadie capaz
     * de volver a entrar.
     */
    @Transactional
    public UserResponse changeStatus(UUID id, String newStatus) {
        AuthenticatedUser admin = authService.currentIdentity();

        if (admin.id().equals(id)) {
            throw new BadRequestException("No puedes cambiar el estado de tu propia cuenta");
        }

        User user = findInMyOrganization(id);
        protectedDemoAccounts.requireNotProtected(user);
        UserStatus status = UserStatus.fromValue(newStatus);

        if (status == UserStatus.ACTIVE && user.getPasswordHash() == null) {
            throw new BadRequestException(
                    "Esta cuenta aún no ha aceptado su invitación, así que no se puede activar. "
                            + "Vuelve a enviarle la invitación.");
        }

        user.setStatus(status);
        userRepository.save(user);

        return UserResponse.of(user);
    }


    /**
     * Elimina a una persona del centro (ver {@link PersonalDataService#deleteUser}).
     *
     * Dos reglas para no dejar el centro sin gestión, la misma lógica que ya
     * aplica {@link #changeStatus}: nadie puede eliminarse a sí mismo, y no se
     * puede eliminar al último administrador activo.
     */
    @Transactional
    public void delete(UUID id) {
        AuthenticatedUser admin = authService.currentIdentity();

        if (admin.id().equals(id)) {
            throw new BadRequestException("No puedes eliminar tu propia cuenta");
        }

        User user = findInMyOrganization(id);
        protectedDemoAccounts.requireNotProtected(user);

        if (user.isDeleted()) {
            throw NotFoundException.of("Usuario", id);
        }
        if (user.getRole() == UserRole.ADMIN && user.getStatus() == UserStatus.ACTIVE
                && userRepository.countByOrganizationIdAndRoleAndStatus(
                        user.getOrganization().getId(), UserRole.ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new BadRequestException(
                    "Es la única persona administradora activa. Nombra a otra antes de eliminarla.");
        }

        personalDataService.deleteUser(user);
    }
    /**
     * Vuelve a emitir la invitación de una cuenta que todavía no la ha
     * aceptado. No se crea un usuario nuevo: se renueva el enlace del que ya
     * existe.
     */
    @Transactional
    public InvitationResponse resendInvitation(UUID id) {
        User user = findInMyOrganization(id);

        if (user.getStatus() != UserStatus.PENDING) {
            throw new ConflictException("Esta cuenta ya está activada; no procede reenviar la invitación");
        }

        return invitationService.issueFor(user);
    }

    /**
     * Exporta el listado (con los mismos filtros que {@link #list}) en CSV,
     * sin paginar: quien exporta quiere el conjunto completo, no una página.
     */
    @Transactional(readOnly = true)
    public String exportCsv(String role, String status, String search) {
        AuthenticatedUser admin = authService.currentIdentity();

        Specification<User> filter = Specification
                .where(UserSpecifications.ofOrganization(admin.organizationId()))
                .and(UserSpecifications.notDeleted())
                .and(UserSpecifications.withRole(roleOrNull(role)))
                .and(UserSpecifications.withStatus(statusOrNull(status)))
                .and(UserSpecifications.thatContains(search));

        List<User> users = userRepository.findAll(filter, Sort.by("name"));

        StringBuilder output = new StringBuilder();
        output.append(Csv.fila("name", "email", "role", "status", "createdAt")).append('\n');
        for (User user : users) {
            String creado = user.getCreatedAt() == null ? "" : user.getCreatedAt().toString();
            output.append(Csv.fila(user.getName(), user.getEmail(), user.getRole().getValue(),
                    user.getStatus().getValue(), creado)).append('\n');
        }
        return output.toString();
    }

    /**
     * Alta masiva desde un CSV con columnas {@code name}, {@code email},
     * {@code role} (cabecera obligatoria, en cualquier orden).
     *
     * Cada fila se intenta por separado con {@link #createAccount}, la misma
     * regla que el alta individual: un email duplicado o un rol inválido en
     * una fila no impide que se den de alta las demás. El resultado dice
     * fila a fila qué se creó y qué no, para corregir solo lo que falló.
     */
    @Transactional
    public ImportUsersResponse importUsers(MultipartFile file) {
        AuthenticatedUser admin = authService.currentIdentity();
        Organization organization = organizationOf(admin);

        List<List<String>> lines;
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            lines = Csv.read(reader);
        } catch (IOException e) {
            throw new BadRequestException("No se ha podido leer el fichero: " + e.getMessage());
        }

        if (lines.isEmpty()) {
            throw new BadRequestException("El fichero está vacío");
        }

        Map<String, Integer> column = columnIndex(lines.get(0));
        if (!column.keySet().containsAll(List.of("name", "email", "role"))) {
            throw new BadRequestException("La cabecera del CSV debe tener las columnas name, email y role");
        }

        List<ImportedRow> filas = new ArrayList<>();
        int creados = 0;

        for (int i = 1; i < lines.size(); i++) {
            List<String> fila = lines.get(i);
            int rowNumber = i + 1; // +1: la cabecera es la fila 1 del fichero
            String email = valueOf(fila, column.get("email"));

            try {
                String name = valueOf(fila, column.get("name"));
                String role = valueOf(fila, column.get("role"));

                if (name.isBlank() || email.isBlank() || role.isBlank()) {
                    throw new BadRequestException("Faltan columnas: se necesitan name, email y role");
                }

                User user = createAccount(organization, name, email, role);
                invitationService.issueFor(user);
                creados++;
                filas.add(new ImportedRow(rowNumber, email, true, null));
            } catch (ApiException | IllegalArgumentException e) {
                filas.add(new ImportedRow(rowNumber, email, false, e.getMessage()));
            }
        }

        return new ImportUsersResponse(filas.size(), creados, filas);
    }

    /** Índice de cada columna con el nombre de la cabecera en minúsculas, para admitir cualquier orden. */
    private Map<String, Integer> columnIndex(List<String> header) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            index.put(header.get(i).trim().toLowerCase(), i);
        }
        return index;
    }

    private String valueOf(List<String> fila, Integer index) {
        return index != null && index < fila.size() ? fila.get(index) : "";
    }

    private User findInMyOrganization(UUID id) {
        AuthenticatedUser admin = authService.currentIdentity();

        return userRepository.findById(id)
                .filter(u -> u.getOrganization().getId().equals(admin.organizationId()))
                // Un usuario de otra organización se trata como inexistente:
                // responder 403 confirmaría que ese identificador existe.
                .orElseThrow(() -> NotFoundException.of("Usuario", id));
    }

    private Organization organizationOf(AuthenticatedUser admin) {
        return organizationRepository.findById(admin.organizationId())
                .orElseThrow(() -> NotFoundException.of("Organización", admin.organizationId()));
    }
}
