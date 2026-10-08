package com.eduvibe.service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.config.AppProperties;
import com.eduvibe.dto.common.PageResponse;
import com.eduvibe.dto.auth.RegisterRequest;
import com.eduvibe.dto.registration.RegistrationRequestResponse;
import com.eduvibe.dto.user.CreateUserResponse;
import com.eduvibe.dto.user.InvitationResponse;
import com.eduvibe.dto.user.UserResponse;
import com.eduvibe.exception.BadRequestException;
import com.eduvibe.exception.ConflictException;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.exception.TooManyRequestsException;
import com.eduvibe.model.Notification;
import com.eduvibe.model.Organization;
import com.eduvibe.model.RegistrationRequest;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.RegistrationStatus;
import com.eduvibe.model.enums.UserRole;
import com.eduvibe.model.enums.UserStatus;
import com.eduvibe.repository.OrganizationRepository;
import com.eduvibe.repository.RegistrationRequestRepository;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.security.AuthenticatedUser;
import com.eduvibe.util.Tokens;

import lombok.RequiredArgsConstructor;

/**
 * Registro por solicitud: la persona pide una cuenta y un administrador decide.
 *
 * El flujo tiene tres pasos y cada uno cierra un riesgo distinto:
 *
 *  1. La persona rellena el formulario público. Se limita por IP, se descartan
 *     los bots (campo cebo) y se envía un enlace al correo indicado.
 *  2. Confirma el correo con ese enlace. Hasta entonces la solicitud no llega
 *     al administrador, así que nadie puede llenarle la bandeja con correos
 *     ajenos ni inventados.
 *  3. Un administrador la acepta (eligiendo el rol) o la rechaza. Al aceptar se
 *     crea la cuenta por el camino de siempre y se emite su invitación: la
 *     contraseña la elige la persona, como en cualquier otra alta.
 *
 * La organización se deduce del dominio del correo (cada organización tiene un
 * dominio único), de modo que nadie elige a qué centro pertenecer ni puede
 * pedir entrar en uno cuyo dominio no es el suyo. Una organización sin dominio
 * configurado no admite solicitudes: solo se entra por invitación.
 *
 * Igual que "olvidé mi contraseña", el formulario responde siempre lo mismo
 * tanto si el correo ya tiene cuenta, como si su dominio no corresponde a
 * ningún centro, como si es nuevo: distinguirlo permitiría averiguar quién está
 * dado de alta.
 */
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationService.class);

    private static final Duration VENTANA_DE_LIMITE = Duration.ofHours(1);

    /**
     * Mientras no pase este tiempo desde el último enlace, se ignora pedirlo de
     * nuevo para el mismo correo: evita llenar de mensajes la bandeja de otra
     * persona escribiendo su email en bucle.
     */
    private static final Duration ESPERA_ENTRE_ENLACES = Duration.ofMinutes(5);

    /** Tras un rechazo, el mismo correo no puede volver a pedirlo durante este tiempo. */
    private static final Duration ESPERA_TRAS_RECHAZO = Duration.ofDays(30);

    /** Las solicitudes que nunca se verificaron se borran pasado este tiempo. */
    private static final Duration RETENCION_SIN_VERIFICAR = Duration.ofDays(3);

    private static final EnumSet<RegistrationStatus> ABIERTAS =
            EnumSet.of(RegistrationStatus.UNVERIFIED, RegistrationStatus.PENDING);

    private final RegistrationRequestRepository requestRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final InvitationService invitationService;
    private final NotificationService notificationService;
    private final MailService mailService;
    private final AuthService authService;
    private final AppProperties propiedades;

    // ------------------------------------------------------------ público

    /**
     * Recibe una solicitud y, si procede, envía el enlace para confirmar el correo.
     *
     * Solo lanza error cuando se supera el límite de peticiones, que no revela
     * nada sobre ningún correo. En cualquier otro caso (cuenta existente,
     * dominio desconocido, solicitud ya abierta, bot) vuelve sin hacer ruido.
     *
     * @throws TooManyRequestsException si esa IP, o la plataforma entera, ha
     *         superado el número de solicitudes por hora
     */
    @Transactional
    public void solicitar(RegisterRequest peticion, String ip) {
        if (peticion.pareceUnBot()) {
            return;
        }
        comprobarLimites(ip);

        String email = User.normalizarEmail(peticion.email());

        Optional<Organization> organizacion = organizacionDe(email);
        if (organizacion.isEmpty() || userRepository.existsByEmail(email) || rechazadaHacePoco(email)) {
            return;
        }

        Optional<RegistrationRequest> abierta = requestRepository.findFirstByEmailAndStatusIn(email, ABIERTAS);
        if (abierta.isPresent()) {
            reenviarEnlaceSiToca(abierta.get());
            return;
        }

        RegistrationRequest solicitud = new RegistrationRequest(
                organizacion.get(), email, peticion.name().trim(), mensajeLimpio(peticion.message()), ip);
        emitirVerificacion(solicitud);
    }

    /**
     * Confirma que el correo es de quien lo pidió: la solicitud pasa a la cola
     * del administrador y se le avisa.
     *
     * El token sirve una sola vez. Inexistente, ya usado y caducado dan el
     * mismo error a propósito, por la misma razón que en las invitaciones.
     */
    @Transactional
    public void verificar(String token) {
        RegistrationRequest solicitud = requestRepository.findByTokenHash(Tokens.hashear(token))
                .filter(RegistrationRequest::puedeVerificarse)
                .orElseThrow(() -> new BadRequestException(
                        "El enlace no es válido o ha caducado. Vuelve a solicitar tu registro."));

        solicitud.marcarComoVerificada();
        requestRepository.save(solicitud);

        avisarAAdministradores(solicitud);
    }

    // ------------------------------------------------------ administración

    /** Solicitudes de mi centro en un estado (por defecto, las que esperan decisión). */
    @Transactional(readOnly = true)
    public PageResponse<RegistrationRequestResponse> listar(String estado, Pageable pageable) {
        AuthenticatedUser admin = authService.identidadActual();
        RegistrationStatus filtro = (estado == null || estado.isBlank())
                ? RegistrationStatus.PENDING
                : RegistrationStatus.desdeValor(estado);

        Page<RegistrationRequest> pagina = requestRepository.findByOrganizationIdAndStatusOrderByCreatedAtAsc(
                admin.organizationId(), filtro, Paginacion.sinOrden(pageable));

        return PageResponse.de(pagina, RegistrationRequestResponse::de);
    }

    /**
     * Acepta una solicitud: crea la cuenta con el rol elegido y emite su
     * invitación. La persona elige su contraseña al abrirla.
     */
    @Transactional
    public CreateUserResponse aprobar(UUID id, String rol) {
        AuthenticatedUser admin = authService.identidadActual();
        RegistrationRequest solicitud = pendienteDeMiCentro(id, admin);

        // Misma regla que cualquier alta: email libre, dominio permitido, rol válido
        User usuario = userService.crearCuenta(
                solicitud.getOrganization(), solicitud.getName(), solicitud.getEmail(), rol);
        InvitationResponse invitacion = invitationService.emitirPara(usuario);

        solicitud.aprobar(userRepository.getReferenceById(admin.id()));
        requestRepository.save(solicitud);

        return new CreateUserResponse(UserResponse.de(usuario), invitacion);
    }

    /** Rechaza una solicitud y se lo comunica a la persona. */
    @Transactional
    public void rechazar(UUID id) {
        AuthenticatedUser admin = authService.identidadActual();
        RegistrationRequest solicitud = pendienteDeMiCentro(id, admin);

        solicitud.rechazar(userRepository.getReferenceById(admin.id()));
        requestRepository.save(solicitud);

        mailService.enviarSolicitudRechazada(solicitud.getName(), solicitud.getEmail());
    }

    /** Cada noche se borran las solicitudes que nadie llegó a verificar. */
    @Scheduled(cron = "0 45 3 * * *")
    @Transactional
    public void purgarSinVerificar() {
        int borradas = requestRepository.borrarSinVerificarAnterioresA(
                RegistrationStatus.UNVERIFIED, Instant.now().minus(RETENCION_SIN_VERIFICAR));
        LOG.info("Purgadas {} solicitudes de registro sin verificar", borradas);
    }

    // ------------------------------------------------------------- interno

    private void comprobarLimites(String ip) {
        Instant desde = Instant.now().minus(VENTANA_DE_LIMITE);
        AppProperties.Registration limites = propiedades.registration();

        boolean ipExcedida = ip != null
                && requestRepository.countByIpAddressAndCreatedAtAfter(ip, desde) >= limites.maxPerIpPerHour();
        boolean plataformaExcedida = requestRepository.countByCreatedAtAfter(desde) >= limites.maxPerHour();

        if (ipExcedida || plataformaExcedida) {
            throw new TooManyRequestsException(
                    "Demasiadas solicitudes de registro. Inténtalo de nuevo dentro de un rato.");
        }
    }

    /** El mensaje sin espacios sobrantes; vacío se guarda como ausente. */
    private String mensajeLimpio(String mensaje) {
        return (mensaje == null || mensaje.isBlank()) ? null : mensaje.trim();
    }

    /** El centro cuyo dominio de correo coincide con el del email, si lo hay. */
    private Optional<Organization> organizacionDe(String email) {
        int arroba = email.lastIndexOf('@');
        if (arroba < 0 || arroba == email.length() - 1) {
            return Optional.empty();
        }
        return organizationRepository.findByAllowedDomain(email.substring(arroba + 1));
    }

    private boolean rechazadaHacePoco(String email) {
        return requestRepository.existsByEmailAndStatusAndReviewedAtAfter(
                email, RegistrationStatus.REJECTED, Instant.now().minus(ESPERA_TRAS_RECHAZO));
    }

    private void reenviarEnlaceSiToca(RegistrationRequest solicitud) {
        if (!solicitud.estaSinVerificar()) {
            return;
        }
        Instant ultimoEnlace = solicitud.getUpdatedAt() != null ? solicitud.getUpdatedAt() : solicitud.getCreatedAt();
        if (ultimoEnlace != null && ultimoEnlace.isAfter(Instant.now().minus(ESPERA_ENTRE_ENLACES))) {
            return;
        }
        emitirVerificacion(solicitud);
    }

    /** Genera un token nuevo (invalida el anterior), lo guarda por su hash y envía el enlace. */
    private void emitirVerificacion(RegistrationRequest solicitud) {
        String token = Tokens.generar();
        Instant caducidad = Instant.now()
                .plus(Duration.ofHours(propiedades.registration().verificationExpirationHours()));

        solicitud.emitirVerificacion(Tokens.hashear(token), caducidad);
        requestRepository.save(solicitud);

        mailService.enviarVerificacionDeRegistro(solicitud.getName(), solicitud.getEmail(), construirEnlace(token));
    }

    private void avisarAAdministradores(RegistrationRequest solicitud) {
        List<User> administradores = userRepository.findByOrganizationIdAndRoleAndStatus(
                solicitud.getOrganization().getId(), UserRole.ADMIN, UserStatus.ACTIVE);

        // "title" es la clave que NotificationResponse usa para redactar el texto
        notificationService.emitirParaVarios(administradores, Notification.REGISTRO_SOLICITADO,
                Map.of("title", solicitud.getName()));
    }

    /**
     * Una solicitud de otro centro se trata como inexistente, igual que un
     * usuario de otro centro en {@link UserService}.
     */
    private RegistrationRequest pendienteDeMiCentro(UUID id, AuthenticatedUser admin) {
        RegistrationRequest solicitud = requestRepository.findById(id)
                .filter(s -> s.getOrganization().getId().equals(admin.organizationId()))
                .orElseThrow(() -> NotFoundException.de("Solicitud de registro", id));

        if (!solicitud.estaPendiente()) {
            throw new ConflictException(solicitud.estaSinVerificar()
                    ? "Esta persona aún no ha confirmado su correo"
                    : "Esta solicitud ya está resuelta");
        }
        return solicitud;
    }

    private String construirEnlace(String token) {
        String base = propiedades.frontendUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/verificar-registro/" + token;
    }
}
