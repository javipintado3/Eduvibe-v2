package com.eduvibe.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Utilidades comunes de paginación.
 *
 * Todos los listados paginados de la API aceptan {@code page} y {@code size}
 * (por defecto 10 por página, con un máximo configurado en
 * {@code spring.data.web.pageable}), y se ordenan siempre por el criterio que
 * fija cada consulta.
 */
public final class Pagination {

    private Pagination() {
    }

    /**
     * Se queda solo con la página y el tamaño pedidos.
     *
     * Spring también acepta {@code sort} en la petición, pero dejar que el
     * cliente elija por qué propiedad ordenar una entidad es abrir la puerta a
     * un 500 con solo escribir mal un nombre: el orden lo decide cada consulta.
     */
    public static Pageable unsorted(Pageable requested) {
        return PageRequest.of(requested.getPageNumber(), requested.getPageSize());
    }

    /**
     * Convierte el texto de búsqueda en un patrón LIKE en minúsculas. Sin texto
     * devuelve "%", que casa con todo: así la consulta no necesita una rama
     * aparte para "sin filtro", ni un parámetro nulo cuyo tipo PostgreSQL no
     * sabría deducir.
     */
    public static String searchPattern(String texto) {
        if (texto == null || texto.isBlank()) {
            return "%";
        }
        return "%" + texto.trim().toLowerCase() + "%";
    }
}
