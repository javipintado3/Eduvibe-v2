package com.eduvibe.dto.perfil;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Todos los datos personales y académicos de una persona, tal y como se los
 * entrega la plataforma al ejercer el derecho de acceso (RGPD).
 *
 * Se devuelve como JSON porque es un formato que cualquiera puede abrir y que
 * otro sistema puede leer, que es lo que pide la portabilidad de los datos.
 */
public record PersonalDataExport(
        Instant exportedAt,
        Profile profile,
        List<Enrollment> enrollments,
        List<Group> groups,
        List<Submission> submissions,
        List<ExamAttempt> examAttempts,
        List<ForumThread> forumThreads,
        List<ForumPost> forumPosts,
        List<Announcement> announcements,
        List<Notification> notifications,
        List<Access> accessHistory) {

    public record Profile(UUID id, String name, String email, String role, String status,
                          String organization, String avatarUrl, boolean twoFactorEnabled,
                          Instant createdAt) {
    }

    public record Enrollment(String className, String roleInClass, Instant enrolledAt) {
    }

    public record Group(String groupName, String className) {
    }

    public record Grade(BigDecimal score, BigDecimal rawScore, String feedback, Instant gradedAt) {
    }

    /** {@code grade} es null mientras nadie haya calificado la entrega. */
    public record Submission(String className, String assignmentTitle, String content, String fileUrl,
                             String status, Instant submittedAt, String teacherNote, Grade grade) {
    }

    public record ExamAttempt(String className, String examTitle, Instant startedAt,
                              Instant submittedAt, BigDecimal score) {
    }

    public record ForumThread(String className, String title, Instant createdAt) {
    }

    public record ForumPost(String threadTitle, String content, Instant createdAt) {
    }

    public record Announcement(String className, String content, Instant createdAt) {
    }

    public record Notification(String type, Map<String, Object> payload, Instant readAt, Instant createdAt) {
    }

    /** Historial de inicios de sesión, que incluye la IP desde la que se hicieron. */
    public record Access(String ipAddress, boolean succeeded, Instant attemptedAt) {
    }
}
