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

    private static final Duration LIMIT_WINDOW = Duration.ofHours(1);

    /**
     * Mientras no pase este tiempo desde el último enlace, se ignora pedirlo de
     * nuevo para el mismo correo: evita llenar de mensajes la bandeja de otra
     * persona escribiendo su email en bucle.
     */
    private static final Duration WAIT_BETWEEN_LINKS = Duration.ofMinutes(5);

    /** Tras un rechazo, el mismo correo no puede volver a pedirlo durante este tiempo. */
    private static final Duration WAIT_AFTER_REJECTION = Duration.ofDays(30);

    /** Las solicitudes que nunca se verificaron se borran pasado este tiempo. */
    private static final Duration RETENTION_UNVERIFIED = Duration.ofDays(3);

    private static final EnumSet<RegistrationStatus> OPEN =
            EnumSet.of(RegistrationStatus.UNVERIFIED, RegistrationStatus.PENDING);

    private final RegistrationRequestRepository requestRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final InvitationService invitationService;
    private final NotificationService notificationService;
    private final MailService mailService;
    private final AuthService authService;
    private final AppProperties properties;

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
    public void request(RegisterRequest request, String ip) {
        if (request.looksLikeBot()) {
            return;
        }
        checkLimits(ip);

        String email = User.normalizeEmail(request.email());

        Optional<Organization> organization = organizationOf(email);
        if (organization.isEmpty() || userRepository.existsByEmail(email) || recentlyRejected(email)) {
            return;
        }

        Optional<RegistrationRequest> open = requestRepository.findFirstByEmailAndStatusIn(email, OPEN);
        if (open.isPresent()) {
            resendLinkIfDue(open.get());
            return;
        }

        RegistrationRequest registrationRequest = new RegistrationRequest(
                organization.get(), email, request.name().trim(), messageClean(request.message()), ip);
        issueVerification(registrationRequest);
    }

    /**
     * Confirma que el correo es de quien lo pidió: la solicitud pasa a la cola
     * del administrador y se le avisa.
     *
     * El token sirve una sola vez. Inexistente, ya usado y caducado dan el
     * mismo error a propósito, por la misma razón que en las invitaciones.
     */
    @Transactional
    public void verify(String token) {
        RegistrationRequest request = requestRepository.findByTokenHash(Tokens.hash(token))
                .filter(RegistrationRequest::canBeVerified)
                .orElseThrow(() -> new BadRequestException(
                        "El enlace no es válido o ha caducado. Vuelve a solicitar tu registro."));

        request.markAsVerified();
        requestRepository.save(request);

        notifyAdministrators(request);
    }

    // ------------------------------------------------------ administración

    /** Solicitudes de mi centro en un estado (por defecto, las que esperan decisión). */
    @Transactional(readOnly = true)
    public PageResponse<RegistrationRequestResponse> list(String status, Pageable pageable) {
        AuthenticatedUser admin = authService.currentIdentity();
        RegistrationStatus filter = (status == null || status.isBlank())
                ? RegistrationStatus.PENDING
                : RegistrationStatus.fromValue(status);

        Page<RegistrationRequest> pagina = requestRepository.findByOrganizationIdAndStatusOrderByCreatedAtAsc(
                admin.organizationId(), filter, Pagination.unsorted(pageable));

        return PageResponse.of(pagina, RegistrationRequestResponse::of);
    }

    /**
     * Acepta una solicitud: crea la cuenta con el rol elegido y emite su
     * invitación. La persona elige su contraseña al abrirla.
     */
    @Transactional
    public CreateUserResponse approve(UUID id, String role) {
        AuthenticatedUser admin = authService.currentIdentity();
        RegistrationRequest request = pendingOfMyOrganization(id, admin);

        // Misma regla que cualquier alta: email libre, dominio permitido, rol válido
        User user = userService.createAccount(
                request.getOrganization(), request.getName(), request.getEmail(), role);
        InvitationResponse invitation = invitationService.issueFor(user);

        request.approve(userRepository.getReferenceById(admin.id()));
        requestRepository.save(request);

        return new CreateUserResponse(UserResponse.of(user), invitation);
    }

    /** Rechaza una solicitud y se lo comunica a la persona. */
    @Transactional
    public void reject(UUID id) {
        AuthenticatedUser admin = authService.currentIdentity();
        RegistrationRequest request = pendingOfMyOrganization(id, admin);

        request.reject(userRepository.getReferenceById(admin.id()));
        requestRepository.save(request);

        mailService.sendRejectedRequest(request.getName(), request.getEmail());
    }

    /** Cada noche se borran las solicitudes que nadie llegó a verificar. */
    @Scheduled(cron = "0 45 3 * * *")
    @Transactional
    public void purgeUnverified() {
        int deleted = requestRepository.deleteUnverifiedOlderThan(
                RegistrationStatus.UNVERIFIED, Instant.now().minus(RETENTION_UNVERIFIED));
        LOG.info("Purgadas {} solicitudes de registro sin verificar", deleted);
    }

    // ------------------------------------------------------------- interno

    private void checkLimits(String ip) {
        Instant from = Instant.now().minus(LIMIT_WINDOW);
        AppProperties.Registration limits = properties.registration();

        boolean ipExceeded = ip != null
                && requestRepository.countByIpAddressAndCreatedAtAfter(ip, from) >= limits.maxPerIpPerHour();
        boolean platformExceeded = requestRepository.countByCreatedAtAfter(from) >= limits.maxPerHour();

        if (ipExceeded || platformExceeded) {
            throw new TooManyRequestsException(
                    "Demasiadas solicitudes de registro. Inténtalo de nuevo dentro de un rato.");
        }
    }

    /** El mensaje sin espacios sobrantes; vacío se guarda como ausente. */
    private String messageClean(String message) {
        return (message == null || message.isBlank()) ? null : message.trim();
    }

    /** El centro cuyo dominio de correo coincide con el del email, si lo hay. */
    private Optional<Organization> organizationOf(String email) {
        int atSign = email.lastIndexOf('@');
        if (atSign < 0 || atSign == email.length() - 1) {
            return Optional.empty();
        }
        return organizationRepository.findByAllowedDomain(email.substring(atSign + 1));
    }

    private boolean recentlyRejected(String email) {
        return requestRepository.existsByEmailAndStatusAndReviewedAtAfter(
                email, RegistrationStatus.REJECTED, Instant.now().minus(WAIT_AFTER_REJECTION));
    }

    private void resendLinkIfDue(RegistrationRequest request) {
        if (!request.isUnverified()) {
            return;
        }
        Instant lastLink = request.getUpdatedAt() != null ? request.getUpdatedAt() : request.getCreatedAt();
        if (lastLink != null && lastLink.isAfter(Instant.now().minus(WAIT_BETWEEN_LINKS))) {
            return;
        }
        issueVerification(request);
    }

    /** Genera un token nuevo (invalida el anterior), lo guarda por su hash y envía el enlace. */
    private void issueVerification(RegistrationRequest request) {
        String token = Tokens.generate();
        Instant expiry = Instant.now()
                .plus(Duration.ofHours(properties.registration().verificationExpirationHours()));

        request.issueVerification(Tokens.hash(token), expiry);
        requestRepository.save(request);

        mailService.sendVerificationOfRegistration(request.getName(), request.getEmail(), buildLink(token));
    }

    private void notifyAdministrators(RegistrationRequest request) {
        List<User> administrators = userRepository.findByOrganizationIdAndRoleAndStatus(
                request.getOrganization().getId(), UserRole.ADMIN, UserStatus.ACTIVE);

        // "title" es la clave que NotificationResponse usa para redactar el texto
        notificationService.issueForSeveral(administrators, Notification.REGISTRATION_REQUESTED,
                Map.of("title", request.getName()));
    }

    /**
     * Una solicitud de otro centro se trata como inexistente, igual que un
     * usuario de otro centro en {@link UserService}.
     */
    private RegistrationRequest pendingOfMyOrganization(UUID id, AuthenticatedUser admin) {
        RegistrationRequest request = requestRepository.findById(id)
                .filter(s -> s.getOrganization().getId().equals(admin.organizationId()))
                .orElseThrow(() -> NotFoundException.of("Solicitud de registro", id));

        if (!request.isPending()) {
            throw new ConflictException(request.isUnverified()
                    ? "Esta persona aún no ha confirmado su correo"
                    : "Esta solicitud ya está resuelta");
        }
        return request;
    }

    private String buildLink(String token) {
        String base = properties.frontendUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/verificar-registro/" + token;
    }
}
