package com.eduvibe.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eduvibe.dto.notification.NotificationResponse;
import com.eduvibe.service.NotificationService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import com.eduvibe.dto.common.PageResponse;

/** Notificaciones de quien está autenticado. Nadie ve ni gestiona las de otra persona. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<PageResponse<NotificationResponse>> misNotificaciones(
            @RequestParam(defaultValue = "false") boolean soloNoLeidas, Pageable pageable) {
        return ResponseEntity.ok(notificationService.misNotificaciones(soloNoLeidas, pageable));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> noLeidas() {
        return ResponseEntity.ok(Map.of("count", notificationService.noLeidas()));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Void> marcarLeida(@PathVariable UUID notificationId) {
        notificationService.marcarLeida(notificationId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> marcarTodasLeidas() {
        notificationService.marcarTodasLeidas();
        return ResponseEntity.noContent().build();
    }
}
