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
    public ResponseEntity<PageResponse<NotificationResponse>> myNotifications(
            @RequestParam(name = "soloNoLeidas", defaultValue = "false") boolean onlyUnread, Pageable pageable) {
        return ResponseEntity.ok(notificationService.myNotifications(onlyUnread, pageable));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unread() {
        return ResponseEntity.ok(Map.of("count", notificationService.unread()));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID notificationId) {
        notificationService.markRead(notificationId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        notificationService.markAllRead();
        return ResponseEntity.noContent().build();
    }
}
