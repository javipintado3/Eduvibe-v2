package com.eduvibe.service;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.dto.profile.ProfileSummaryResponse;
import com.eduvibe.dto.user.UserResponse;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.EnrollmentRole;
import com.eduvibe.model.enums.UserRole;
import com.eduvibe.model.enums.UserStatus;
import com.eduvibe.repository.AssignmentRepository;
import com.eduvibe.repository.EnrollmentRepository;
import com.eduvibe.repository.GradeRepository;
import com.eduvibe.repository.NotificationRepository;
import com.eduvibe.repository.SchoolClassRepository;
import com.eduvibe.repository.SubmissionRepository;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

/**
 * Resumen de la pantalla de perfil: qué le interesa ver a cada rol al entrar.
 *
 * No hay una única "actividad reciente" genérica: el alumnado quiere saber
 * qué le queda por hacer, el profesorado cuánto tiene por corregir, y
 * administración el estado del centro. Cada consulta cuenta en base de
 * datos, no trae filas para contarlas en Java.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final AuthService authService;
    private final EnrollmentRepository enrollmentRepository;
    private final AssignmentRepository assignmentRepository;
    private final GradeRepository gradeRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public ProfileSummaryResponse summary() {
        AuthenticatedUser user = authService.currentIdentity();

        return switch (user.role()) {
            case ADMIN -> adminSummary(user);
            case TEACHER -> teacherSummary(user);
            case STUDENT, GUARDIAN -> studentSummary(user);
        };
    }

    private ProfileSummaryResponse studentSummary(AuthenticatedUser user) {
        long schoolClasses = enrollmentRepository.countByUserIdAndRoleInClass(user.id(), EnrollmentRole.STUDENT);
        Double notaMedia = gradeRepository.averageGradeOf(user.id());
        long pending = assignmentRepository.countPendingForStudent(user.id());
        long unread = notificationRepository.countByUserIdAndReadAtIsNull(user.id());

        return ProfileSummaryResponse.forStudent((int) schoolClasses, notaMedia, (int) pending, unread);
    }

    private ProfileSummaryResponse teacherSummary(AuthenticatedUser user) {
        long schoolClasses = enrollmentRepository.countByUserIdAndRoleInClass(user.id(), EnrollmentRole.TEACHER);
        long alumnado = enrollmentRepository.countStudentsForTeacher(user.id());
        long toGrade = submissionRepository.countToGradeForTeacher(user.id());

        return ProfileSummaryResponse.forTeacher((int) schoolClasses, (int) alumnado, (int) toGrade);
    }

    private ProfileSummaryResponse adminSummary(AuthenticatedUser user) {
        Map<String, Long> usuariosPorRol = Arrays.stream(UserRole.values())
                .collect(Collectors.toMap(
                        UserRole::getValue,
                        role -> userRepository.countByOrganizationIdAndRoleAndDeletedAtIsNull(user.organizationId(), role)));

        long schoolClasses = schoolClassRepository.countByOrganizationId(user.organizationId());
        long pending = userRepository.countByOrganizationIdAndStatus(user.organizationId(), UserStatus.PENDING);

        return ProfileSummaryResponse.forAdmin(usuariosPorRol, (int) schoolClasses, pending);
    }

    /** Sin foto, o "" para quitar la que hubiera: en los dos casos se vuelve a las iniciales. */
    @Transactional
    public UserResponse updateAvatar(String url) {
        AuthenticatedUser autenticado = authService.currentIdentity();
        User user = userRepository.findById(autenticado.id())
                .orElseThrow(() -> NotFoundException.of("Usuario", autenticado.id()));

        user.setAvatarUrl((url == null || url.isBlank()) ? null : url);
        userRepository.save(user);

        return UserResponse.of(user);
    }
}
