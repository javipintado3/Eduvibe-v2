package com.eduvibe.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.dto.notification.NotificationResponse;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.model.Notification;
import com.eduvibe.model.User;
import com.eduvibe.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import com.eduvibe.dto.common.PageResponse;

/**
 * Notificaciones dirigidas a quien está autenticado.
 *
 * Nadie gestiona notificaciones ajenas —ni siquiera administración—, así que a
 * diferencia de avisos, materiales o eventos, aquí no hace falta
 * {@link ClassAccessService}: basta con comprobar que la notificación es de
 * quien pregunta.
 *
 * La emiten AssignmentService (tarea nueva), AnnouncementService (aviso
 * nuevo) y SubmissionService (nota publicada), cada uno con el payload que
 * {@link NotificationResponse} necesita para redactar el texto.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AuthService authService;

    /** Una notificación para una sola persona (por ejemplo, la nota de su propia entrega). */
    @Transactional
    public void emitir(User destinatario, String tipo, Map<String, Object> payload) {
        notificationRepository.save(new Notification(destinatario, tipo, payload));
    }

    /**
     * La misma notificación para varias personas (por ejemplo, todo el
     * alumnado de una clase). El payload se comparte entre todas las filas
     * porque no se modifica después de crearlas.
     */
    @Transactional
    public void emitirParaVarios(List<User> destinatarios, String tipo, Map<String, Object> payload) {
        List<Notification> notificaciones = destinatarios.stream()
                .map(destinatario -> new Notification(destinatario, tipo, payload))
                .toList();
        notificationRepository.saveAll(notificaciones);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> misNotificaciones(boolean soloNoLeidas, Pageable pageable) {
        UUID userId = authService.identidadActual().id();
        Pageable sinOrden = Paginacion.sinOrden(pageable);

        return PageResponse.de(
                soloNoLeidas
                        ? notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, sinOrden)
                        : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, sinOrden),
                NotificationResponse::de);
    }

    @Transactional(readOnly = true)
    public long noLeidas() {
        return notificationRepository.countByUserIdAndReadAtIsNull(authService.identidadActual().id());
    }

    @Transactional
    public void marcarLeida(UUID notificationId) {
        Notification aviso = notificationRepository.findById(notificationId)
                .orElseThrow(() -> NotFoundException.de("Notificación", notificationId));

        if (!aviso.getUser().getId().equals(authService.identidadActual().id())) {
            throw new AccessDeniedException("Esta notificación no es tuya");
        }

        aviso.marcarComoLeida();
        notificationRepository.save(aviso);
    }

    @Transactional
    public void marcarTodasLeidas() {
        notificationRepository.marcarTodasLeidas(authService.identidadActual().id());
    }
}
