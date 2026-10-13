package com.eduvibe.dto.profile;

/** Null o vacío quita la foto: se vuelve a mostrar las iniciales. */
public record AvatarRequest(String url) {
}
