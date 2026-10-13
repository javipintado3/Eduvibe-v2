package com.eduvibe.dto.announcement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveAnnouncementRequest(

        @NotBlank(message = "El aviso no puede estar vacío")
        @Size(max = 5000, message = "El aviso es demasiado largo")
        String content,

        /** Los avisos fijados aparecen arriba del muro. */
        Boolean pinned) {

    public boolean isPinned() {
        return Boolean.TRUE.equals(pinned);
    }
}
