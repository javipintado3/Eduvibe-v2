package com.eduvibe.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

import com.eduvibe.model.ForumThread;

public interface ForumThreadRepository extends JpaRepository<ForumThread, UUID> {

    List<ForumThread> findByAuthorIdOrderByCreatedAtAsc(UUID authorId);

    /** Borrar la cuenta vacía el título de los hilos, que es texto libre, sin romper el hilo de los demás. */
    @Modifying
    @Query("UPDATE ForumThread t SET t.title = :texto WHERE t.author.id = :authorId")
    int sustituirTituloDe(@Param("authorId") UUID authorId, @Param("texto") String texto);

    List<ForumThread> findBySchoolClassId(UUID classId);
}
