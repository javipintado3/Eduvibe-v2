package com.eduvibe.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eduvibe.dto.classgroup.ClassGroupResponse;
import com.eduvibe.dto.classgroup.SaveClassGroupRequest;
import com.eduvibe.service.ClassGroupService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Un subgrupo ya creado. Se crea colgado de su clase
 * ({@code POST /api/classes/{id}/groups}); a partir de ahí se edita entero o
 * se retira por su propio identificador.
 */
@RestController
@RequestMapping("/api/class-groups")
@RequiredArgsConstructor
public class ClassGroupController {

    private final ClassGroupService classGroupService;

    @PutMapping("/{groupId}")
    public ResponseEntity<ClassGroupResponse> update(@PathVariable UUID groupId,
                                                          @Valid @RequestBody SaveClassGroupRequest request) {
        return ResponseEntity.ok(classGroupService.update(groupId, request));
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> delete(@PathVariable UUID groupId) {
        classGroupService.delete(groupId);
        return ResponseEntity.noContent().build();
    }
}
