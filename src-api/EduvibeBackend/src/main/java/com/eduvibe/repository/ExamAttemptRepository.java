package com.eduvibe.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eduvibe.model.ExamAttempt;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, UUID> {

    List<ExamAttempt> findByStudentIdOrderByStartedAtAsc(UUID studentId);

    Optional<ExamAttempt> findByExamIdAndStudentId(UUID examId, UUID studentId);

    long countByExamId(UUID examId);

    /** Si ya hay algún intento de examen en la clase, para no dejar borrarla sin más. */
    boolean existsByExamSchoolClassId(UUID classId);

    /** Todos los intentos de un examen, para la vista de corrección del profesorado. */
    @EntityGraph(attributePaths = "student")
    List<ExamAttempt> findByExamIdOrderByStudentNameAsc(UUID examId);

    /** Mis intentos en varios exámenes a la vez, para la lista de exámenes de una clase sin N+1 consultas. */
    @Query("SELECT a FROM ExamAttempt a WHERE a.exam.id IN :examIds AND a.student.id = :studentId")
    List<ExamAttempt> findByExamIdInAndStudentId(@Param("examIds") List<UUID> examIds,
                                                 @Param("studentId") UUID studentId);
}
