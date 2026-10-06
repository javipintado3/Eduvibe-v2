package com.eduvibe.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eduvibe.model.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    /** Todas las entregas de una persona, para exportar sus datos. */
    List<Submission> findByStudentIdOrderByCreatedAtAsc(UUID studentId);

    /**
     * Entregas que no forman parte del expediente: las que nadie ha calificado.
     * Al borrar una cuenta se eliminan; las evaluadas se conservan.
     */
    @Query("SELECT s FROM Submission s WHERE s.student.id = :studentId "
         + "AND NOT EXISTS (SELECT g.id FROM Grade g WHERE g.submission = s)")
    List<Submission> findSinCalificarDe(@Param("studentId") UUID studentId);

    Optional<Submission> findByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);

    /** Todas las entregas de una tarea, para la pantalla de corrección. */
    @EntityGraph(attributePaths = { "student", "assignment" })
    List<Submission> findByAssignmentIdOrderByStudentNameAsc(UUID assignmentId);

    /** Las entregas de un alumno en una clase, para su pestaña de notas. */
    @Query("""
            SELECT s FROM Submission s
            WHERE s.student.id = :studentId
              AND s.assignment.schoolClass.id = :classId
            ORDER BY s.assignment.dueDate ASC NULLS LAST
            """)
    List<Submission> findDeAlumnoEnClase(@Param("studentId") UUID studentId,
                                         @Param("classId") UUID classId);

    /** Las entregas de un alumno en varias tareas de golpe, para evitar N+1. */
    @Query("SELECT s FROM Submission s WHERE s.student.id = :studentId AND s.assignment.id IN :assignmentIds")
    List<Submission> findDeAlumnoEnTareas(@Param("studentId") UUID studentId,
                                          @Param("assignmentIds") List<UUID> assignmentIds);

    /** Todas las entregas de una clase de golpe, para la analítica del profesorado. */
    @EntityGraph(attributePaths = { "student", "assignment" })
    @Query("SELECT s FROM Submission s WHERE s.assignment.schoolClass.id = :classId")
    List<Submission> findDeClase(@Param("classId") UUID classId);

    long countByAssignmentId(UUID assignmentId);

    /** Si ya hay alguna entrega en la clase, para no dejar borrarla sin más. */
    boolean existsByAssignmentSchoolClassId(UUID classId);

    /**
     * Las otras entregas del mismo subgrupo en la misma tarea, para propagarles
     * lo que se acaba de guardar o calificar sin tocar la entrega de origen.
     */
    List<Submission> findByAssignmentIdAndClassGroupIdAndIdNot(UUID assignmentId, UUID classGroupId, UUID exceptId);

    /**
     * Las entregas enviadas y sin corregir de todas las clases que imparte el
     * profesor, las más antiguas primero: es la cola de trabajo que tiene que
     * vaciar, y lo que lleva más tiempo esperando va delante.
     *
     * En una tarea grupal cada miembro tiene su propia fila, pero calificar una
     * califica a todo el subgrupo, así que solo se cuenta una por subgrupo (la de
     * menor id). Se resuelve en la propia consulta y no después en memoria para
     * que la paginación y el total salgan bien: quitar filas de una página ya
     * cortada dejaría páginas cortas y un total que no cuadra.
     */
    String POR_CORREGIR = """
            s.status = com.eduvibe.model.enums.SubmissionStatus.SUBMITTED
              AND s.assignment.schoolClass.id IN (
                  SELECT e.schoolClass.id FROM Enrollment e
                  WHERE e.user.id = :teacherId
                    AND e.roleInClass = com.eduvibe.model.enums.EnrollmentRole.TEACHER)
              AND (s.classGroup IS NULL OR NOT EXISTS (
                  SELECT 1 FROM Submission otra
                  WHERE otra.assignment = s.assignment
                    AND otra.classGroup = s.classGroup
                    AND otra.status = com.eduvibe.model.enums.SubmissionStatus.SUBMITTED
                    AND otra.id < s.id))
            """;

    @EntityGraph(attributePaths = { "student", "assignment", "assignment.schoolClass", "classGroup" })
    @Query(value = "SELECT s FROM Submission s WHERE " + POR_CORREGIR + " ORDER BY s.submittedAt ASC, s.id ASC",
           countQuery = "SELECT COUNT(s) FROM Submission s WHERE " + POR_CORREGIR)
    Page<Submission> findPorCorregirDeProfesor(@Param("teacherId") UUID teacherId, Pageable pageable);

    /** Para el resumen de perfil del profesorado: cuánto trabajo tiene por corregir (mismo criterio que la cola). */
    @Query("SELECT COUNT(s) FROM Submission s WHERE " + POR_CORREGIR)
    long countPorCorregirDeProfesor(@Param("teacherId") UUID teacherId);
}
