package com.eduvibe.dto.forum;

import java.time.Instant;
import java.util.UUID;

import com.eduvibe.model.ForumPost;

/**
 * @param puedoBorrar si quien consulta puede borrarlo: su autor, o el
 *                    profesorado de la clase como moderación
 */
public record ForumPostResponse(
        UUID id,
        String content,
        String authorName,
        Instant createdAt,
        boolean puedoBorrar) {

    public static ForumPostResponse of(ForumPost message, boolean puedoBorrar) {
        return new ForumPostResponse(
                message.getId(),
                message.getContent(),
                message.getAuthor().getName(),
                message.getCreatedAt(),
                puedoBorrar);
    }
}
