package com.eduvibe.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eduvibe.model.Exam;

public interface ExamRepository extends JpaRepository<Exam, UUID> {

    /** Los que tienen fecha límite primero y por fecha; los que no, al final. */
    @Query("""
            SELECT e FROM Exam e
            WHERE e.schoolClass.id = :classId
            ORDER BY e.dueDate ASC NULLS LAST, e.createdAt DESC
            """)
    List<Exam> findForSchoolClass(@Param("classId") UUID classId);

    long countByTopicId(UUID topicId);
}
