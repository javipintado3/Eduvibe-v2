package com.eduvibe.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.eduvibe.dto.perfil.PersonalDataExport;
import com.eduvibe.exception.BadRequestException;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.exception.TooManyRequestsException;
import com.eduvibe.model.GdprRequest;
import com.eduvibe.model.Grade;
import com.eduvibe.model.Submission;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.UserRole;
import com.eduvibe.model.enums.UserStatus;
import com.eduvibe.repository.AnnouncementRepository;
import com.eduvibe.repository.ClassGroupMemberRepository;
import com.eduvibe.repository.EnrollmentRepository;
import com.eduvibe.repository.ExamAttemptRepository;
import com.eduvibe.repository.ForumPostRepository;
import com.eduvibe.repository.ForumThreadRepository;
import com.eduvibe.repository.GdprRequestRepository;
import com.eduvibe.repository.GradeRepository;
import com.eduvibe.repository.InvitationRepository;
import com.eduvibe.repository.LoginAttemptRepository;
import com.eduvibe.repository.NotificationRepository;
import com.eduvibe.repository.PasswordResetRepository;
import com.eduvibe.repository.RevokedTokenRepository;
import com.eduvibe.repository.SubmissionRepository;
import com.eduvibe.repository.TotpRecoveryCodeRepository;
import com.eduvibe.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Derechos de la persona sobre sus datos (RGPD): acceso/portabilidad y supresión.
 *
 * La supresión no es un borrado total ni una anonimización total, y el flujo se
 * lo explica antes de confirmar. Un expediente anonimizado no sirve de nada: el
 * centro tiene que poder emitir certificados con nombre real. Así que se borra
 * todo lo que NO forma parte del expediente obligatorio (contacto, foto, IP,
 * historial de accesos, notificaciones, borradores, mensajes de foro) y se
 * conserva identificable solo lo que la ley obliga a conservar: las notas y las
 * entregas evaluadas.
 *
 * Los plazos legales concretos, y la purga de ese expediente cuando se cumplan,
 * los valida quien lleve la protección de datos del centro (no es asesoramiento
 * legal). Esta clase no incluye todavía esa purga.
 */
@Service
@RequiredArgsConstructor
public class DatosPersonalesService {

    /** Lo que queda donde había texto libre de la persona en el foro. */
    static final String TEXTO_ELIMINADO = "[Contenido eliminado a petición de la persona]";

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassGroupMemberRepository classGroupMemberRepository;
    private final SubmissionRepository submissionRepository;
    private final GradeRepository gradeRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final ForumThreadRepository forumThreadRepository;
    private final ForumPostRepository forumPostRepository;
    private final AnnouncementRepository announcementRepository;
    private final NotificationRepository notificationRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final InvitationRepository invitationRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final TotpRecoveryCodeRepository totpRecoveryCodeRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final GdprRequestRepository gdprRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final TwoFactorService twoFactorService;
    private final FileStorageService fileStorageService;

    // ------------------------------------------------------------- exportar

    @Transactional
    public PersonalDataExport exportar(UUID usuarioId) {
        User usuario = cargar(usuarioId);

        List<Submission> entregas = submissionRepository.findByStudentIdOrderByCreatedAtAsc(usuarioId);
        Map<UUID, Grade> notas = entregas.isEmpty() ? Map.of()
                : gradeRepository.findDeEntregas(entregas.stream().map(Submission::getId).toList()).stream()
                        .collect(Collectors.toMap(nota -> nota.getSubmission().getId(), Function.identity()));

        PersonalDataExport datos = new PersonalDataExport(
                Instant.now(),
                new PersonalDataExport.Profile(usuario.getId(), usuario.getName(), usuario.getEmail(),
                        usuario.getRole().getValor(), usuario.getStatus().getValor(),
                        usuario.getOrganization().getName(), usuario.getAvatarUrl(),
                        usuario.isTotpEnabled(), usuario.getCreatedAt()),

                enrollmentRepository.findByUserIdOrderBySchoolClassNameAsc(usuarioId).stream()
                        .map(m -> new PersonalDataExport.Enrollment(
                                m.getSchoolClass().getName(), m.getRoleInClass().name().toLowerCase(), m.getEnrolledAt()))
                        .toList(),

                classGroupMemberRepository.findByUserId(usuarioId).stream()
                        .map(m -> new PersonalDataExport.Group(
                                m.getClassGroup().getName(), m.getClassGroup().getSchoolClass().getName()))
                        .toList(),

                entregas.stream()
                        .map(e -> new PersonalDataExport.Submission(
                                e.getAssignment().getSchoolClass().getName(), e.getAssignment().getTitle(),
                                e.getContent(), e.getFileUrl(), e.getStatus().name().toLowerCase(),
                                e.getSubmittedAt(), e.getTeacherNote(), aDto(notas.get(e.getId()))))
                        .toList(),

                examAttemptRepository.findByStudentIdOrderByStartedAtAsc(usuarioId).stream()
                        .map(i -> new PersonalDataExport.ExamAttempt(
                                i.getExam().getSchoolClass().getName(), i.getExam().getTitle(),
                                i.getStartedAt(), i.getSubmittedAt(), i.getScore()))
                        .toList(),

                forumThreadRepository.findByAuthorIdOrderByCreatedAtAsc(usuarioId).stream()
                        .map(h -> new PersonalDataExport.ForumThread(
                                h.getSchoolClass().getName(), h.getTitle(), h.getCreatedAt()))
                        .toList(),

                forumPostRepository.findByAuthorIdOrderByCreatedAtAsc(usuarioId).stream()
                        .map(p -> new PersonalDataExport.ForumPost(
                                p.getThread().getTitle(), p.getContent(), p.getCreatedAt()))
                        .toList(),

                announcementRepository.findByAuthorIdOrderByCreatedAtAsc(usuarioId).stream()
                        .map(a -> new PersonalDataExport.Announcement(
                                a.getSchoolClass().getName(), a.getContent(), a.getCreatedAt()))
                        .toList(),

                notificationRepository.findByUserIdOrderByCreatedAtDesc(usuarioId, Pageable.unpaged()).stream()
                        .map(n -> new PersonalDataExport.Notification(
                                n.getType(), n.getPayload(), n.getReadAt(), n.getCreatedAt()))
                        .toList(),

                loginAttemptRepository.findByEmailOrderByAttemptedAtDesc(usuario.getEmail()).stream()
                        .map(a -> new PersonalDataExport.Access(a.getIpAddress(), a.isSucceeded(), a.getAttemptedAt()))
                        .toList());

        gdprRequestRepository.save(GdprRequest.atendida(usuario, GdprRequest.EXPORTAR));
        return datos;
    }

    private PersonalDataExport.Grade aDto(Grade nota) {
        return nota == null ? null
                : new PersonalDataExport.Grade(nota.getScore(), nota.getRawScore(), nota.getFeedback(), nota.getGradedAt());
    }

    // --------------------------------------------------------------- borrar

    /**
     * Da de baja la cuenta conservando solo el expediente. Pide la contraseña
     * (y el código del 2FA si lo tiene activado), porque es irreversible y no
     * debe poder hacerlo quien se encuentre una sesión abierta. Un fallo cuenta
     * para el límite de intentos del login.
     */
    @Transactional
    public void borrarCuenta(UUID usuarioId, String password, String codigo, String ip) {
        User usuario = cargar(usuarioId);

        if (usuario.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("La cuenta no está activa");
        }

        if (loginAttemptService.estaBloqueado(usuario.getEmail())) {
            throw new TooManyRequestsException("Demasiados intentos fallidos. Vuelve a intentarlo en "
                    + loginAttemptService.minutosDeBloqueo() + " minutos");
        }

        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            loginAttemptService.registrarFallo(usuario.getEmail(), ip);
            throw new BadRequestException("La contraseña no es correcta");
        }
        if (usuario.isTotpEnabled() && !twoFactorService.codigoValido(usuario, codigo)) {
            loginAttemptService.registrarFallo(usuario.getEmail(), ip);
            throw new BadRequestException("El código de verificación no es correcto");
        }

        // Sin administración el centro no se puede gestionar ni recuperar
        if (usuario.getRole() == UserRole.ADMIN && userRepository.countByOrganizationIdAndRoleAndStatus(
                usuario.getOrganization().getId(), UserRole.ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new BadRequestException(
                    "Eres la única persona administradora activa. Nombra a otra antes de borrar tu cuenta.");
        }

        // Archivos que dejan de tener dueño: la foto y los adjuntos de entregas sin calificar
        List<Submission> borradores = submissionRepository.findSinCalificarDe(usuarioId);
        List<String> archivos = new java.util.ArrayList<>();
        archivos.add(usuario.getAvatarUrl());
        borradores.forEach(b -> archivos.add(b.getFileUrl()));

        // Lo que no es expediente
        submissionRepository.deleteAll(borradores);
        forumPostRepository.sustituirContenidoDe(usuarioId, TEXTO_ELIMINADO);
        forumThreadRepository.sustituirTituloDe(usuarioId, TEXTO_ELIMINADO);
        notificationRepository.borrarDe(usuarioId);
        invitationRepository.borrarDe(usuarioId);
        passwordResetRepository.borrarDe(usuarioId);
        totpRecoveryCodeRepository.borrarDe(usuarioId);
        revokedTokenRepository.borrarDe(usuarioId);
        loginAttemptRepository.borrarTodosDe(usuario.getEmail());

        usuario.darDeBajaPorSupresion();
        userRepository.save(usuario);
        gdprRequestRepository.save(GdprRequest.atendida(usuario, GdprRequest.BORRAR));

        // Los archivos se borran de disco solo si la transacción llega a confirmarse
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                archivos.forEach(fileStorageService::borrar);
            }
        });
    }

    private User cargar(UUID usuarioId) {
        return userRepository.findById(usuarioId)
                .orElseThrow(() -> NotFoundException.de("Usuario", usuarioId));
    }
}
