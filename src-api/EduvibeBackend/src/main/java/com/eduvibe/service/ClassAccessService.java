package com.eduvibe.service;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.exception.NotFoundException;
import com.eduvibe.model.SchoolClass;
import com.eduvibe.model.enums.EnrollmentRole;
import com.eduvibe.repository.EnrollmentRepository;
import com.eduvibe.repository.SchoolClassRepository;
import com.eduvibe.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

/**
 * Quién puede ver y quién puede tocar cada clase.
 *
 * Toda la fase de clases, tareas y entregas hace la misma comprobación una y
 * otra vez, así que vive en un único sitio en lugar de repetida en cada
 * servicio. Si mañana cambia la regla —por ejemplo, que un tutor pueda ver las
 * clases de su hijo— se cambia aquí y queda cambiada en todas partes.
 *
 * Las reglas son:
 *
 *  - Ver una clase: estar matriculado en ella, o ser administrador del centro.
 *  - Editarla y crear contenido: ser profesor de esa clase, o administrador.
 *  - Entregar: estar matriculado como alumno.
 *
 * Nótese que ser TEACHER en la organización no basta para editar una clase: hay
 * que impartirla. Un profesor no puede poner tareas en la clase de otro.
 */
@Service
@RequiredArgsConstructor
public class ClassAccessService {

    private final SchoolClassRepository schoolClassRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AuthService authService;

    /**
     * Devuelve la clase si quien pregunta puede verla.
     *
     * Una clase de otra organización se trata como inexistente y no como
     * prohibida: responder 403 confirmaría que ese identificador existe.
     */
    @Transactional(readOnly = true)
    public SchoolClass requireVisible(UUID classId) {
        AuthenticatedUser user = authService.currentIdentity();
        SchoolClass schoolClass = findInMyOrganization(classId, user);

        if (user.isAdmin() || isMemberOf(classId, user.id())) {
            return schoolClass;
        }
        throw new AccessDeniedException("No participas en esta clase");
    }

    /** Devuelve la clase si quien pregunta puede modificarla o añadirle contenido. */
    @Transactional(readOnly = true)
    public SchoolClass requireEditable(UUID classId) {
        AuthenticatedUser user = authService.currentIdentity();
        SchoolClass schoolClass = findInMyOrganization(classId, user);

        if (user.isAdmin() || isTeacherOf(classId, user.id())) {
            return schoolClass;
        }
        throw new AccessDeniedException("Solo el profesorado de esta clase puede hacer esto");
    }

    /**
     * Devuelve la clase si quien pregunta es administración.
     *
     * Editar contenido lo puede hacer el profesorado de la clase, pero
     * borrarla entera —con todo lo que cuelga de ella— queda reservado a
     * administración, igual que el alta.
     */
    @Transactional(readOnly = true)
    public SchoolClass requireAdmin(UUID classId) {
        AuthenticatedUser user = authService.currentIdentity();
        SchoolClass schoolClass = findInMyOrganization(classId, user);

        if (user.isAdmin()) {
            return schoolClass;
        }
        throw new AccessDeniedException("Solo administración puede borrar una clase");
    }

    /** Comprueba que quien pregunta es alumno de la clase. */
    @Transactional(readOnly = true)
    public void requireStudentOf(UUID classId) {
        AuthenticatedUser user = authService.currentIdentity();
        findInMyOrganization(classId, user);

        boolean isStudent = enrollmentRepository.existsBySchoolClassIdAndUserIdAndRoleInClass(
                classId, user.id(), EnrollmentRole.STUDENT);

        if (!isStudent) {
            throw new AccessDeniedException("Solo el alumnado matriculado puede entregar en esta clase");
        }
    }

    public boolean isTeacherOf(UUID classId, UUID userId) {
        return enrollmentRepository.existsBySchoolClassIdAndUserIdAndRoleInClass(
                classId, userId, EnrollmentRole.TEACHER);
    }

    public boolean isMemberOf(UUID classId, UUID userId) {
        return enrollmentRepository.existsBySchoolClassIdAndUserId(classId, userId);
    }

    /**
     * Si quien pregunta puede corregir en esa clase: profesorado de la clase o
     * administración. Se expone porque los DTO de entrega lo necesitan para
     * decidir qué información incluir.
     */
    public boolean canGradeIn(UUID classId) {
        AuthenticatedUser user = authService.currentIdentity();
        return user.isAdmin() || isTeacherOf(classId, user.id());
    }

    private SchoolClass findInMyOrganization(UUID classId, AuthenticatedUser user) {
        return schoolClassRepository.findById(classId)
                .filter(c -> c.getOrganization().getId().equals(user.organizationId()))
                .orElseThrow(() -> NotFoundException.of("Clase", classId));
    }
}
