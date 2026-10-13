package com.eduvibe.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.dto.calendar.CalendarEntryResponse;
import com.eduvibe.dto.calendar.SaveCalendarEventRequest;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.model.Assignment;
import com.eduvibe.model.CalendarEvent;
import com.eduvibe.model.Organization;
import com.eduvibe.model.SchoolClass;
import com.eduvibe.model.enums.CalendarEventType;
import com.eduvibe.repository.AssignmentRepository;
import com.eduvibe.repository.CalendarEventRepository;
import com.eduvibe.repository.OrganizationRepository;
import com.eduvibe.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

/**
 * Agenda del centro: eventos introducidos a mano y fechas de entrega.
 *
 * Las dos fuentes se unifican en {@link CalendarEntryResponse} y se devuelven
 * ya mezcladas y ordenadas, para que el cliente no tenga que combinar dos
 * listas. Ver quién ve qué es la misma regla en las dos consultas: todo el
 * centro para administración, lo propio de todo el centro más las clases en
 * las que se participa para el resto.
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    private final CalendarEventRepository calendarEventRepository;
    private final AssignmentRepository assignmentRepository;
    private final OrganizationRepository organizationRepository;
    private final ClassAccessService access;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public List<CalendarEntryResponse> agenda(Instant from, Instant until) {
        AuthenticatedUser user = authService.currentIdentity();
        UUID orgId = user.organizationId();

        List<CalendarEvent> events;
        List<Assignment> assignments;

        if (user.isAdmin()) {
            events = calendarEventRepository.findByOrganizationIdAndEventDateBetweenOrderByEventDateAsc(
                    orgId, from, until);
            assignments = assignmentRepository.findWithSubmissionForOrganization(orgId, from, until);
        } else {
            events = calendarEventRepository.findForUser(orgId, user.id(), from, until);
            assignments = assignmentRepository.findWithSubmissionForUser(orgId, user.id(), from, until);
        }

        return Stream.concat(
                        events.stream().map(CalendarEntryResponse::ofEvent),
                        assignments.stream().map(CalendarEntryResponse::ofAssignment))
                .sorted(Comparator.comparing(CalendarEntryResponse::fecha))
                .toList();
    }

    @Transactional
    public CalendarEntryResponse create(SaveCalendarEventRequest request) {
        AuthenticatedUser user = authService.currentIdentity();
        SchoolClass schoolClass = schoolClassOfEvent(request.classId(), user);

        Organization organization = organizationRepository.findById(user.organizationId())
                .orElseThrow(() -> NotFoundException.of("Organización", user.organizationId()));

        CalendarEvent event = new CalendarEvent(organization, schoolClass, request.title().trim(),
                request.eventDate(), typeOrNone(request.type()));
        calendarEventRepository.saveAndFlush(event);

        return CalendarEntryResponse.ofEvent(event);
    }

    @Transactional
    public CalendarEntryResponse update(UUID eventId, SaveCalendarEventRequest request) {
        CalendarEvent event = calendarEventRepository.findById(eventId)
                .orElseThrow(() -> NotFoundException.of("Evento", eventId));
        requireCanManage(event);

        // La clase del evento no se toca aquí: cambiarlo de clase, o de clase a
        // todo el centro, cambia quién puede gestionarlo, así que para eso se
        // borra y se crea de nuevo en lugar de reinterpretar la petición.
        event.setTitle(request.title().trim());
        event.setEventDate(request.eventDate());
        event.setType(typeOrNone(request.type()));
        calendarEventRepository.save(event);

        return CalendarEntryResponse.ofEvent(event);
    }

    @Transactional
    public void delete(UUID eventId) {
        CalendarEvent event = calendarEventRepository.findById(eventId)
                .orElseThrow(() -> NotFoundException.of("Evento", eventId));
        requireCanManage(event);

        calendarEventRepository.delete(event);
    }

    /** Un evento de clase lo gestiona su profesorado o administración; uno de todo el centro, solo administración. */
    private SchoolClass schoolClassOfEvent(UUID classId, AuthenticatedUser user) {
        if (classId == null) {
            if (!user.isAdmin()) {
                throw new AccessDeniedException("Solo la administración crea eventos de todo el centro");
            }
            return null;
        }
        return access.requireEditable(classId);
    }

    private void requireCanManage(CalendarEvent event) {
        SchoolClass schoolClass = event.getSchoolClass();
        if (schoolClass == null) {
            if (!authService.currentIdentity().isAdmin()) {
                throw new AccessDeniedException("Solo la administración gestiona los eventos de todo el centro");
            }
        } else {
            access.requireEditable(schoolClass.getId());
        }
    }

    private CalendarEventType typeOrNone(String type) {
        return (type == null || type.isBlank()) ? null : CalendarEventType.fromValue(type);
    }
}
