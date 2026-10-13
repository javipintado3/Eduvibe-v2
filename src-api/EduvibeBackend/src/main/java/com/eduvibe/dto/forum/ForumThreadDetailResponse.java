package com.eduvibe.dto.forum;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.eduvibe.model.ForumThread;

/**
 * @param puedoModerar si quien consulta puede borrar cualquier mensaje o el
 *                      hilo entero, aunque no sea suyo: el profesorado de la
 *                      clase
 */
public record ForumThreadDetailResponse(
        UUID id,
        UUID classId,
        String className,
        UUID topicId,
        String title,
        String authorName,
        boolean puedoModerar,
        boolean puedoBorrarHilo,
        List<ForumPostResponse> posts,
        Instant createdAt) {

    public static ForumThreadDetailResponse of(ForumThread thread, boolean puedoModerar, boolean puedoBorrarHilo,
                                               List<ForumPostResponse> posts) {
        return new ForumThreadDetailResponse(
                thread.getId(),
                thread.getSchoolClass().getId(),
                thread.getSchoolClass().getName(),
                thread.getTopic() == null ? null : thread.getTopic().getId(),
                thread.getTitle(),
                thread.getAuthor().getName(),
                puedoModerar,
                puedoBorrarHilo,
                posts,
                thread.getCreatedAt());
    }
}
