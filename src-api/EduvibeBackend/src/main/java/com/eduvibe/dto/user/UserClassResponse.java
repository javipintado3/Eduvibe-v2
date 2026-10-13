package com.eduvibe.dto.user;

import java.util.UUID;

import com.eduvibe.model.Enrollment;

/** Una clase en la que participa la persona, para su ficha en el panel de administración. */
public record UserClassResponse(
        UUID classId,
        String name,
        String subject,
        String color,
        String roleInClass) {

    public static UserClassResponse of(Enrollment enrollment) {
        return new UserClassResponse(
                enrollment.getSchoolClass().getId(),
                enrollment.getSchoolClass().getName(),
                enrollment.getSchoolClass().getSubject(),
                enrollment.getSchoolClass().getColor(),
                enrollment.getRoleInClass().getValue());
    }
}
