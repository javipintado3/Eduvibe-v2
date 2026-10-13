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

import com.eduvibe.dto.profile.PersonalDataExport;
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
public class PersonalDataService {

    /** Lo que queda donde había texto libre de la persona en el foro. */
    static final String DELETED_TEXT = "[Contenido eliminado a petición de la persona]";

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
    private final ProtectedDemoAccounts protectedDemoAccounts;
    private final LoginAttemptService loginAttemptService;
    private final TwoFactorService twoFactorService;
    private final FileStorageService fileStorageService;

    // ------------------------------------------------------------- exportar

    @Transactional
    public PersonalDataExport export(UUID userId) {
        User user = load(userId);

        List<Submission> submissions = submissionRepository.findByStudentIdOrderByCreatedAtAsc(userId);
        Map<UUID, Grade> grades = submissions.isEmpty() ? Map.of()
                : gradeRepository.findForSubmissions(submissions.stream().map(Submission::getId).toList()).stream()
                        .collect(Collectors.toMap(grade -> grade.getSubmission().getId(), Function.identity()));

        PersonalDataExport data = new PersonalDataExport(
                Instant.now(),
                new PersonalDataExport.Profile(user.getId(), user.getName(), user.getEmail(),
                        user.getRole().getValue(), user.getStatus().getValue(),
                        user.getOrganization().getName(), user.getAvatarUrl(),
                        user.isTotpEnabled(), user.getCreatedAt()),

                enrollmentRepository.findByUserIdOrderBySchoolClassNameAsc(userId).stream()
                        .map(m -> new PersonalDataExport.Enrollment(
                                m.getSchoolClass().getName(), m.getRoleInClass().name().toLowerCase(), m.getEnrolledAt()))
                        .toList(),

                classGroupMemberRepository.findByUserId(userId).stream()
                        .map(m -> new PersonalDataExport.Group(
                                m.getClassGroup().getName(), m.getClassGroup().getSchoolClass().getName()))
                        .toList(),

                submissions.stream()
                        .map(e -> new PersonalDataExport.Submission(
                                e.getAssignment().getSchoolClass().getName(), e.getAssignment().getTitle(),
                                e.getContent(), e.getFileUrl(), e.getStatus().name().toLowerCase(),
                                e.getSubmittedAt(), e.getTeacherNote(), aDto(grades.get(e.getId()))))
                        .toList(),

                examAttemptRepository.findByStudentIdOrderByStartedAtAsc(userId).stream()
                        .map(i -> new PersonalDataExport.ExamAttempt(
                                i.getExam().getSchoolClass().getName(), i.getExam().getTitle(),
                                i.getStartedAt(), i.getSubmittedAt(), i.getScore()))
                        .toList(),

                forumThreadRepository.findByAuthorIdOrderByCreatedAtAsc(userId).stream()
                        .map(h -> new PersonalDataExport.ForumThread(
                                h.getSchoolClass().getName(), h.getTitle(), h.getCreatedAt()))
                        .toList(),

                forumPostRepository.findByAuthorIdOrderByCreatedAtAsc(userId).stream()
                        .map(p -> new PersonalDataExport.ForumPost(
                                p.getThread().getTitle(), p.getContent(), p.getCreatedAt()))
                        .toList(),

                announcementRepository.findByAuthorIdOrderByCreatedAtAsc(userId).stream()
                        .map(a -> new PersonalDataExport.Announcement(
                                a.getSchoolClass().getName(), a.getContent(), a.getCreatedAt()))
                        .toList(),

                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged()).stream()
                        .map(n -> new PersonalDataExport.Notification(
                                n.getType(), n.getPayload(), n.getReadAt(), n.getCreatedAt()))
                        .toList(),

                loginAttemptRepository.findByEmailOrderByAttemptedAtDesc(user.getEmail()).stream()
                        .map(a -> new PersonalDataExport.Access(a.getIpAddress(), a.isSucceeded(), a.getAttemptedAt()))
                        .toList());

        gdprRequestRepository.save(GdprRequest.handled(user, GdprRequest.EXPORT));
        return data;
    }

    private PersonalDataExport.Grade aDto(Grade grade) {
        return grade == null ? null
                : new PersonalDataExport.Grade(grade.getScore(), grade.getRawScore(), grade.getFeedback(), grade.getGradedAt());
    }

    // --------------------------------------------------------------- borrar

    /**
     * Da de baja la cuenta conservando solo el expediente. Pide la contraseña
     * (y el código del 2FA si lo tiene activado), porque es irreversible y no
     * debe poder hacerlo quien se encuentre una sesión abierta. Un fallo cuenta
     * para el límite de intentos del login.
     */
    @Transactional
    public void deleteAccount(UUID userId, String password, String code, String ip) {
        User user = load(userId);
        protectedDemoAccounts.requireNotProtected(user);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("La cuenta no está activa");
        }

        if (loginAttemptService.isLocked(user.getEmail())) {
            throw new TooManyRequestsException("Demasiados intentos fallidos. Vuelve a intentarlo en "
                    + loginAttemptService.minutesOfLockout() + " minutos");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            loginAttemptService.recordFailure(user.getEmail(), ip);
            throw new BadRequestException("La contraseña no es correcta");
        }
        if (user.isTotpEnabled() && !twoFactorService.validCode(user, code)) {
            loginAttemptService.recordFailure(user.getEmail(), ip);
            throw new BadRequestException("El código de verificación no es correcto");
        }

        // Sin administración el centro no se puede gestionar ni recuperar
        if (user.getRole() == UserRole.ADMIN && userRepository.countByOrganizationIdAndRoleAndStatus(
                user.getOrganization().getId(), UserRole.ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new BadRequestException(
                    "Eres la única persona administradora activa. Nombra a otra antes de borrar tu cuenta.");
        }

        // Archivos que dejan de tener dueño: la foto y los adjuntos de entregas sin calificar
        List<Submission> drafts = submissionRepository.findUngradedFor(userId);
        List<String> files = new java.util.ArrayList<>();
        files.add(user.getAvatarUrl());
        drafts.forEach(b -> files.add(b.getFileUrl()));

        // Lo que no es expediente
        submissionRepository.deleteAll(drafts);
        forumPostRepository.replaceContentOf(userId, DELETED_TEXT);
        forumThreadRepository.replaceTitleOf(userId, DELETED_TEXT);
        notificationRepository.deleteByUser(userId);
        invitationRepository.deleteByUser(userId);
        passwordResetRepository.deleteByUser(userId);
        totpRecoveryCodeRepository.deleteByUser(userId);
        revokedTokenRepository.deleteByUser(userId);
        loginAttemptRepository.deleteAllByEmail(user.getEmail());

        user.deactivateForErasure();
        userRepository.save(user);
        gdprRequestRepository.save(GdprRequest.handled(user, GdprRequest.DELETE));

        // Los archivos se borran de disco solo si la transacción llega a confirmarse
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                files.forEach(fileStorageService::delete);
            }
        });
    }

    // ------------------------------------------------- eliminar (administración)

    /**
     * Elimina a una persona por decisión de la administración.
     *
     * Es la otra cara de {@link #deleteAccount}: allí la persona ejerce su derecho
     * de supresión y se conserva su expediente identificable; aquí el centro
     * decide que ya no forma parte de él, así que se anonimiza también el nombre.
     * Lo que escribió o creó (foros, anuncios, exámenes, notas puestas) se queda
     * como "Usuario eliminado" en lugar de borrarse, porque pertenece también a
     * otras personas: un hilo de foro sin sus mensajes no se entiende.
     *
     * Se la saca de sus clases y grupos y se borran sus datos de acceso. Las
     * comprobaciones de quién puede hacerlo (misma organización, no a uno mismo,
     * no al último administrador) las hace quien llama.
     */
    @Transactional
    public void deleteUser(User user) {
        UUID id = user.getId();
        String avatar = user.getAvatarUrl();

        enrollmentRepository.deleteByUser(id);
        classGroupMemberRepository.deleteByUser(id);
        notificationRepository.deleteByUser(id);
        invitationRepository.deleteByUser(id);
        passwordResetRepository.deleteByUser(id);
        totpRecoveryCodeRepository.deleteByUser(id);
        revokedTokenRepository.deleteByUser(id);
        loginAttemptRepository.deleteAllByEmail(user.getEmail());

        user.delete();
        userRepository.save(user);

        // La foto se borra de disco solo si la transacción llega a confirmarse
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileStorageService.delete(avatar);
            }
        });
    }

    private User load(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.of("Usuario", userId));
    }
}
