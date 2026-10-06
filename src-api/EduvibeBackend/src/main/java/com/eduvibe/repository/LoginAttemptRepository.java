package com.eduvibe.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eduvibe.model.LoginAttempt;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

    List<LoginAttempt> findByEmailOrderByAttemptedAtDesc(String email);

    /** Borrar la cuenta elimina todo su historial de accesos, que incluye su IP. */
    @Modifying
    @Query("DELETE FROM LoginAttempt a WHERE a.email = :email")
    int borrarTodosDe(@Param("email") String email);

    /** Fallos de un email desde el instante indicado. */
    long countByEmailAndSucceededFalseAndAttemptedAtAfter(String email, Instant desde);

    /** Un acceso correcto deja el contador a cero: se descartan los fallos previos. */
    @Modifying
    @Query("DELETE FROM LoginAttempt a WHERE a.email = :email AND a.succeeded = false")
    int borrarFallosDe(@Param("email") String email);

    /** Retención: nada se conserva más allá de la fecha indicada. */
    @Modifying
    @Query("DELETE FROM LoginAttempt a WHERE a.attemptedAt < :antesDe")
    int borrarAnterioresA(@Param("antesDe") Instant antesDe);
}
