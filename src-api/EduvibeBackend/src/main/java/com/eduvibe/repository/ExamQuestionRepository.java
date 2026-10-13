package com.eduvibe.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eduvibe.model.ExamQuestion;

public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, UUID> {

    List<ExamQuestion> findByExamIdOrderBySortOrderAsc(UUID examId);

    long countByExamId(UUID examId);

    /**
     * El banco de preguntas de una clase: todas las que ya se usaron en
     * alguno de sus exámenes, de los más recientes a los más antiguos.
     */
    @Query("""
            SELECT q FROM ExamQuestion q
            WHERE q.exam.schoolClass.id = :classId
            ORDER BY q.exam.createdAt DESC, q.sortOrder ASC
            """)
    List<ExamQuestion> findBankForSchoolClass(@Param("classId") UUID classId);
}
