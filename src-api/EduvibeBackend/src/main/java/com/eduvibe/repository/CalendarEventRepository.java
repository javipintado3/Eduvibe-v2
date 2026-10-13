package com.eduvibe.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eduvibe.model.CalendarEvent;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {

    /**
     * Eventos que le corresponden a una persona en un intervalo: los de todo el
     * centro, más los de las clases en las que participa.
     *
     * Se resuelve en una consulta en lugar de pedir primero las clases y
     * después los eventos de cada una.
     */
    @Query("""
            SELECT e FROM CalendarEvent e
            WHERE e.organization.id = :orgId
              AND e.eventDate BETWEEN :desde AND :hasta
              AND (e.schoolClass IS NULL
                   OR e.schoolClass.id IN (
                        SELECT m.schoolClass.id FROM Enrollment m WHERE m.user.id = :userId))
            ORDER BY e.eventDate ASC
            """)
    List<CalendarEvent> findForUser(@Param("orgId") UUID orgId,
                                        @Param("userId") UUID userId,
                                        @Param("desde") Instant from,
                                        @Param("hasta") Instant until);

    /** Para la administración, que ve todo el centro sin estar matriculada. */
    List<CalendarEvent> findByOrganizationIdAndEventDateBetweenOrderByEventDateAsc(
            UUID orgId, Instant from, Instant until);
}
