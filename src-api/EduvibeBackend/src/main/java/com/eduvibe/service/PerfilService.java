package com.eduvibe.service;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.dto.perfil.ResumenPerfilResponse;
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
public class PerfilService {

    private final AuthService authService;
    private final EnrollmentRepository enrollmentRepository;
    private final AssignmentRepository assignmentRepository;
    private final GradeRepository gradeRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public ResumenPerfilResponse resumen() {
        AuthenticatedUser usuario = authService.identidadActual();

        return switch (usuario.role()) {
            case ADMIN -> resumenAdmin(usuario);
            case TEACHER -> resumenProfesor(usuario);
            case STUDENT, GUARDIAN -> resumenAlumno(usuario);
        };
    }

    private ResumenPerfilResponse resumenAlumno(AuthenticatedUser usuario) {
        long clases = enrollmentRepository.countByUserIdAndRoleInClass(usuario.id(), EnrollmentRole.STUDENT);
        Double notaMedia = gradeRepository.notaMediaDe(usuario.id());
        long pendientes = assignmentRepository.countPendientesDeAlumno(usuario.id());
        long noLeidas = notificationRepository.countByUserIdAndReadAtIsNull(usuario.id());

        return ResumenPerfilResponse.paraAlumno((int) clases, notaMedia, (int) pendientes, noLeidas);
    }

    private ResumenPerfilResponse resumenProfesor(AuthenticatedUser usuario) {
        long clases = enrollmentRepository.countByUserIdAndRoleInClass(usuario.id(), EnrollmentRole.TEACHER);
        long alumnado = enrollmentRepository.countAlumnadoDeProfesor(usuario.id());
        long porCorregir = submissionRepository.countPorCorregirDeProfesor(usuario.id());

        return ResumenPerfilResponse.paraProfesor((int) clases, (int) alumnado, (int) porCorregir);
    }

    private ResumenPerfilResponse resumenAdmin(AuthenticatedUser usuario) {
        Map<String, Long> usuariosPorRol = Arrays.stream(UserRole.values())
                .collect(Collectors.toMap(
                        UserRole::getValor,
                        rol -> userRepository.countByOrganizationIdAndRoleAndDeletedAtIsNull(usuario.organizationId(), rol)));

        long clases = schoolClassRepository.countByOrganizationId(usuario.organizationId());
        long pendientes = userRepository.countByOrganizationIdAndStatus(usuario.organizationId(), UserStatus.PENDING);

        return ResumenPerfilResponse.paraAdmin(usuariosPorRol, (int) clases, pendientes);
    }

    /** Sin foto, o "" para quitar la que hubiera: en los dos casos se vuelve a las iniciales. */
    @Transactional
    public UserResponse actualizarAvatar(String url) {
        AuthenticatedUser autenticado = authService.identidadActual();
        User usuario = userRepository.findById(autenticado.id())
                .orElseThrow(() -> NotFoundException.de("Usuario", autenticado.id()));

        usuario.setAvatarUrl((url == null || url.isBlank()) ? null : url);
        userRepository.save(usuario);

        return UserResponse.de(usuario);
    }
}
