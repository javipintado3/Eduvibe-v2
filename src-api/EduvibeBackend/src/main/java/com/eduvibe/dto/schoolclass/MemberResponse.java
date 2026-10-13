package com.eduvibe.dto.schoolclass;

import java.time.Instant;
import java.util.UUID;

import com.eduvibe.model.Enrollment;

/**
 * Una persona dentro de una clase.
 *
 * @param userId       identificador de la persona, no de la matrícula
 * @param roleInClass  'teacher' o 'student'
 */
public record MemberResponse(
        UUID userId,
        String name,
        String email,
        String roleInClass,
        Instant enrolledAt) {

    public static MemberResponse of(Enrollment enrollment) {
        return new MemberResponse(
                enrollment.getUser().getId(),
                enrollment.getUser().getName(),
                enrollment.getUser().getEmail(),
                enrollment.getRoleInClass().getValue(),
                enrollment.getEnrolledAt());
    }
}
