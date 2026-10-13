package com.eduvibe.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eduvibe.service.AnnouncementService;

import lombok.RequiredArgsConstructor;

/**
 * Un aviso ya publicado. Se crea colgado de su clase
 * ({@code POST /api/classes/{id}/announcements}); a partir de ahí se retira
 * por su propio identificador.
 */
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @DeleteMapping("/{announcementId}")
    public ResponseEntity<Void> delete(@PathVariable UUID announcementId) {
        announcementService.delete(announcementId);
        return ResponseEntity.noContent().build();
    }
}
