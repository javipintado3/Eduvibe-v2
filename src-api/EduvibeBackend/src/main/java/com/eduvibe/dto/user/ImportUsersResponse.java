package com.eduvibe.dto.user;

import java.util.List;

/**
 * Resultado de un alta masiva por CSV.
 *
 * Una fila mala no aborta el resto: cada fila se intenta por separado y el
 * resultado dice fila a fila qué se creó y qué no, para que quien lo sube
 * pueda corregir solo lo que falló en vez de tener que revisar el fichero
 * entero otra vez.
 */
public record ImportUsersResponse(int total, int creados, List<ImportedRow> filas) {

    public record ImportedRow(int fila, String email, boolean creado, String motivo) {
    }
}
