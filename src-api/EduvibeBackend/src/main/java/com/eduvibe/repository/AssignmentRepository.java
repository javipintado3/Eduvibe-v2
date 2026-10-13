package com.eduvibe.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eduvibe.model.Assignment;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    /**
     * Tareas de una clase. Las que tienen plazo van primero y por fecha; las que
     * no lo tienen, al final: NULLS LAST es justo lo que se quiere ver en una
     * lista de trabajo pendiente.
     */
    @Query("""
            SELECT a FROM Assignment a
            WHERE a.schoolClass.id = :classId
            ORDER BY a.dueDate ASC NULLS LAST, a.createdAt DESC
            """)
    List<Assignment> findForSchoolClass(@Param("classId") UUID classId);

    long countBySchoolClassId(UUID classId);

    /** Se usa antes de borrar un tema, para no dejar tareas colgando de él. */
    long countByTopicId(UUID topicId);

    /**
     * Entregas con plazo dentro de un intervalo, para las clases en las que
     * participa la persona. Alimenta la agenda: las fechas de entrega no se
     * guardan como eventos, se derivan de aquí.
     */
    @Query("""
            SELECT a FROM Assignment a
            WHERE a.schoolClass.organization.id = :orgId
              AND a.dueDate BETWEEN :desde AND :hasta
              AND a.schoolClass.id IN (
                   SELECT m.schoolClass.id FROM Enrollment m WHERE m.user.id = :userId)
            ORDER BY a.dueDate ASC
            """)
    List<Assignment> findWithSubmissionForUser(@Param("orgId") UUID orgId,
                                               @Param("userId") UUID userId,
                                               @Param("desde") Instant from,
                                               @Param("hasta") Instant until);

    /** Para la administración, que ve las entregas de todo el centro. */
    @Query("""
            SELECT a FROM Assignment a
            WHERE a.schoolClass.organization.id = :orgId
              AND a.dueDate BETWEEN :desde AND :hasta
            ORDER BY a.dueDate ASC
            """)
    List<Assignment> findWithSubmissionForOrganization(@Param("orgId") UUID orgId,
                                                  @Param("desde") Instant from,
                                                  @Param("hasta") Instant until);

    /**
     * Tareas todavía por hacer, para el resumen de perfil del alumnado: sin
     * vencer (o sin plazo) y sin una entrega que no sea un borrador. Un
     * borrador no cuenta como hecho: todavía no se ha entregado nada.
     */
    @Query("""
            SELECT COUNT(a) FROM Assignment a
            WHERE (a.dueDate IS NULL OR a.dueDate > CURRENT_TIMESTAMP)
              AND a.schoolClass.id IN (
                  SELECT e.schoolClass.id FROM Enrollment e
                  WHERE e.user.id = :studentId
                    AND e.roleInClass = com.eduvibe.model.enums.EnrollmentRole.STUDENT)
              AND a.id NOT IN (
                  SELECT s.assignment.id FROM Submission s
                  WHERE s.student.id = :studentId
                    AND s.status <> com.eduvibe.model.enums.SubmissionStatus.DRAFT)
            """)
    long countPendingForStudent(@Param("studentId") UUID studentId);
}
