package com.eduvibe.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eduvibe.model.ForumPost;

public interface ForumPostRepository extends JpaRepository<ForumPost, UUID> {

    List<ForumPost> findByAuthorIdOrderByCreatedAtAsc(UUID authorId);

    /** Borrar la cuenta vacía el texto de los mensajes, sin quitar los de las demás personas del hilo. */
    @Modifying
    @Query("UPDATE ForumPost p SET p.content = :texto WHERE p.author.id = :authorId")
    int sustituirContenidoDe(@Param("authorId") UUID authorId, @Param("texto") String texto);

    List<ForumPost> findByThreadIdOrderByCreatedAtAsc(UUID threadId);

    long countByThreadId(UUID threadId);

    /**
     * Cuántos mensajes tiene cada hilo y cuándo fue el último, para la lista de
     * hilos de una clase. En una sola consulta, no una por hilo.
     */
    @Query("""
            SELECT p.thread.id AS threadId, COUNT(p) AS total, MAX(p.createdAt) AS ultima
            FROM ForumPost p
            WHERE p.thread.id IN :threadIds
            GROUP BY p.thread.id
            """)
    List<EstadisticasDeHilo> estadisticasDe(@Param("threadIds") List<UUID> threadIds);

    interface EstadisticasDeHilo {
        UUID getThreadId();
        long getTotal();
        Instant getUltima();
    }
}
