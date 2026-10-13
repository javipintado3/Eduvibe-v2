package com.eduvibe.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eduvibe.model.Grade;

public interface GradeRepository extends JpaRepository<Grade, UUID> {

    Optional<Grade> findBySubmissionId(UUID submissionId);

    /** Las notas de varias entregas a la vez, para no consultar una por una. */
    @Query("SELECT g FROM Grade g WHERE g.submission.id IN :submissionIds")
    List<Grade> findForSubmissions(@Param("submissionIds") List<UUID> submissionIds);

    /** Nota media del alumno en todo lo que tiene calificado, para su resumen de perfil. */
    @Query("SELECT AVG(g.score) FROM Grade g WHERE g.submission.student.id = :studentId")
    Double averageGradeOf(@Param("studentId") UUID studentId);
}
