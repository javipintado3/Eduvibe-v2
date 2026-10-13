package com.eduvibe.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eduvibe.dto.resource.ResourceResponse;
import com.eduvibe.dto.resource.SaveResourceRequest;
import com.eduvibe.service.ResourceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Un material ya publicado. Se crea colgado de su clase
 * ({@code POST /api/classes/{id}/resources}); a partir de ahí se edita o se
 * retira por su propio identificador.
 */
@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @PutMapping("/{resourceId}")
    public ResponseEntity<ResourceResponse> update(@PathVariable UUID resourceId,
                                                        @Valid @RequestBody SaveResourceRequest request) {
        return ResponseEntity.ok(resourceService.update(resourceId, request));
    }

    @DeleteMapping("/{resourceId}")
    public ResponseEntity<Void> delete(@PathVariable UUID resourceId) {
        resourceService.delete(resourceId);
        return ResponseEntity.noContent().build();
    }
}
