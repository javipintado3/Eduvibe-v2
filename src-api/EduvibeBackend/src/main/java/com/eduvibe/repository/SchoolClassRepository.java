package com.eduvibe.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eduvibe.model.SchoolClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, UUID> {

    /**
     * Todas las clases del centro, paginadas y con búsqueda por nombre o materia:
     * es la vista de un administrador. {@code patron} llega ya como LIKE en minúsculas.
     */
    @Query("""
            SELECT c FROM SchoolClass c
            WHERE c.organization.id = :organizationId
              AND (lower(c.name) LIKE :patron OR lower(coalesce(c.subject, '')) LIKE :patron)
            ORDER BY c.name ASC, c.subject ASC
            """)
    Page<SchoolClass> findForOrganization(@Param("organizationId") UUID organizationId,
                                     @Param("patron") String pattern, Pageable pageable);

    /** Para el resumen de perfil de administración. */
    long countByOrganizationId(UUID organizationId);

    /** Las clases en las que participa una persona, sea profesor o alumno, paginadas y con búsqueda. */
    @Query(value = """
            SELECT e.schoolClass FROM Enrollment e
            WHERE e.user.id = :userId
              AND (lower(e.schoolClass.name) LIKE :patron OR lower(coalesce(e.schoolClass.subject, '')) LIKE :patron)
            ORDER BY e.schoolClass.name ASC, e.schoolClass.subject ASC
            """,
           countQuery = """
            SELECT COUNT(e) FROM Enrollment e
            WHERE e.user.id = :userId
              AND (lower(e.schoolClass.name) LIKE :patron OR lower(coalesce(e.schoolClass.subject, '')) LIKE :patron)
            """)
    Page<SchoolClass> findForUser(@Param("userId") UUID userId,
                                      @Param("patron") String pattern, Pageable pageable);

    /**
     * Próxima fecha de entrega de cada clase, para la línea de estado de las
     * tarjetas del panel.
     *
     * Se resuelve en una sola consulta agrupada en lugar de preguntar clase por
     * clase, que sería el problema de las N+1 consultas en su forma más clásica.
     */
    @Query("""
            SELECT a.schoolClass.id AS classId, MIN(a.dueDate) AS nextDueDate
            FROM Assignment a
            WHERE a.schoolClass.id IN :classIds
              AND a.dueDate IS NOT NULL
              AND a.dueDate > CURRENT_TIMESTAMP
            GROUP BY a.schoolClass.id
            """)
    List<NextSubmission> findNextSubmissions(@Param("classIds") List<UUID> classIds);

    /** Proyección del resultado de la consulta anterior. */
    interface NextSubmission {
        UUID getClassId();

        Instant getNextDueDate();
    }
}
