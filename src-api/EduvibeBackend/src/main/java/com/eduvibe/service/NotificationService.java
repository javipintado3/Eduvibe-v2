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
    public void issue(User recipient, String tipo, Map<String, Object> payload) {
        notificationRepository.save(new Notification(recipient, tipo, payload));
    }

    /**
     * La misma notificación para varias personas (por ejemplo, todo el
     * alumnado de una clase). El payload se comparte entre todas las filas
     * porque no se modifica después de crearlas.
     */
    @Transactional
    public void issueForSeveral(List<User> recipients, String tipo, Map<String, Object> payload) {
        List<Notification> notifications = recipients.stream()
                .map(recipient -> new Notification(recipient, tipo, payload))
                .toList();
        notificationRepository.saveAll(notifications);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> myNotifications(boolean onlyUnread, Pageable pageable) {
        UUID userId = authService.currentIdentity().id();
        Pageable unsorted = Pagination.unsorted(pageable);

        return PageResponse.of(
                onlyUnread
                        ? notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, unsorted)
                        : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, unsorted),
                NotificationResponse::of);
    }

    @Transactional(readOnly = true)
    public long unread() {
        return notificationRepository.countByUserIdAndReadAtIsNull(authService.currentIdentity().id());
    }

    @Transactional
    public void markRead(UUID notificationId) {
        Notification announcement = notificationRepository.findById(notificationId)
                .orElseThrow(() -> NotFoundException.of("Notificación", notificationId));

        if (!announcement.getUser().getId().equals(authService.currentIdentity().id())) {
            throw new AccessDeniedException("Esta notificación no es tuya");
        }

        announcement.markAsRead();
        notificationRepository.save(announcement);
    }

    @Transactional
    public void markAllRead() {
        notificationRepository.markAllRead(authService.currentIdentity().id());
    }
}
