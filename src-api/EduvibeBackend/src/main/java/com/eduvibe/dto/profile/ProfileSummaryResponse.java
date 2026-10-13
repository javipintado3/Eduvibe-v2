package com.eduvibe.dto.profile;

import java.util.Map;

/**
 * Resumen de la pantalla de perfil.
 *
 * Un único DTO para los tres roles, en vez de tres, porque el cliente pide un
 * solo endpoint y pinta una tarjeta u otra según qué campos le llegan
 * rellenos; cada fábrica solo rellena los suyos, el resto queda a null.
 */
public record ProfileSummaryResponse(
        String role,
        Integer numeroClases,
        Integer numeroAlumnos,
        Integer entregasPorCorregir,
        Double notaMedia,
        Integer tareasPendientes,
        Long notificacionesNoLeidas,
        Map<String, Long> usuariosPorRol,
        Long invitacionesPendientes) {

    public static ProfileSummaryResponse forStudent(int numeroClases, Double notaMedia,
                                                    int tareasPendientes, long notificacionesNoLeidas) {
        return new ProfileSummaryResponse("student", numeroClases, null, null,
                notaMedia, tareasPendientes, notificacionesNoLeidas, null, null);
    }

    public static ProfileSummaryResponse forTeacher(int numeroClases, int numeroAlumnos, int entregasPorCorregir) {
        return new ProfileSummaryResponse("teacher", numeroClases, numeroAlumnos, entregasPorCorregir,
                null, null, null, null, null);
    }

    public static ProfileSummaryResponse forAdmin(Map<String, Long> usuariosPorRol, int numeroClases,
                                                   long invitacionesPendientes) {
        return new ProfileSummaryResponse("admin", numeroClases, null, null,
                null, null, null, usuariosPorRol, invitacionesPendientes);
    }
}
