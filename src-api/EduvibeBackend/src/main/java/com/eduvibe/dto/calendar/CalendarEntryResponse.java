package com.eduvibe.dto.calendar;

import java.time.Instant;
import java.util.UUID;

import com.eduvibe.model.Assignment;
import com.eduvibe.model.CalendarEvent;

/**
 * Una entrada de la agenda.
 *
 * Unifica dos orígenes distintos: los eventos introducidos a mano y las fechas
 * de entrega, que no se guardan como eventos sino que se derivan de las tareas.
 * Al cliente le llegan con la misma forma, y así no tiene que mezclar dos
 * listas ni ordenarlas por su cuenta.
 *
 * @param origen     'event' o 'assignment'
 * @param referencia identificador del evento o de la tarea, para poder navegar
 */
public record CalendarEntryResponse(
        UUID referencia,
        String origen,
        String title,
        Instant fecha,
        String tipo,
        UUID classId,
        String className,
        String classSubject,
        String classColor) {

    public static CalendarEntryResponse ofEvent(CalendarEvent event) {
        var schoolClass = event.getSchoolClass();
        return new CalendarEntryResponse(
                event.getId(),
                "event",
                event.getTitle(),
                event.getEventDate(),
                event.getType() == null ? "other" : event.getType().getValue(),
                schoolClass == null ? null : schoolClass.getId(),
                schoolClass == null ? null : schoolClass.getName(),
                schoolClass == null ? null : schoolClass.getSubject(),
                schoolClass == null ? null : schoolClass.getColor());
    }

    public static CalendarEntryResponse ofAssignment(Assignment assignment) {
        var schoolClass = assignment.getSchoolClass();
        return new CalendarEntryResponse(
                assignment.getId(),
                "assignment",
                assignment.getTitle(),
                assignment.getDueDate(),
                "assignment_due",
                schoolClass.getId(),
                schoolClass.getName(),
                schoolClass.getSubject(),
                schoolClass.getColor());
    }
}
