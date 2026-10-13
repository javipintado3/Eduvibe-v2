package com.eduvibe.controller;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eduvibe.dto.calendar.CalendarEntryResponse;
import com.eduvibe.dto.calendar.SaveCalendarEventRequest;
import com.eduvibe.service.CalendarService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Agenda del centro: eventos a mano y fechas de entrega, ya mezclados.
 *
 * No cuelga de una clase como avisos o materiales porque una consulta cubre
 * varias clases a la vez (las de quien pregunta, o todo el centro si es
 * administración).
 */
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    @GetMapping
    public ResponseEntity<List<CalendarEntryResponse>> agenda(@RequestParam Instant from,
                                                               @RequestParam Instant to) {
        return ResponseEntity.ok(calendarService.agenda(from, to));
    }

    @PostMapping
    public ResponseEntity<CalendarEntryResponse> create(@Valid @RequestBody SaveCalendarEventRequest request) {
        CalendarEntryResponse creado = calendarService.create(request);
        return ResponseEntity.created(URI.create("/api/calendar/" + creado.referencia())).body(creado);
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<CalendarEntryResponse> update(@PathVariable UUID eventId,
                                                             @Valid @RequestBody SaveCalendarEventRequest request) {
        return ResponseEntity.ok(calendarService.update(eventId, request));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> delete(@PathVariable UUID eventId) {
        calendarService.delete(eventId);
        return ResponseEntity.noContent().build();
    }
}
