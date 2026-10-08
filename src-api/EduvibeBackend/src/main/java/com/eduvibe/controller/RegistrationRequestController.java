package com.eduvibe.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eduvibe.dto.common.PageResponse;
import com.eduvibe.dto.registration.ApproveRegistrationRequest;
import com.eduvibe.dto.registration.RegistrationRequestResponse;
import com.eduvibe.dto.user.CreateUserResponse;
import com.eduvibe.service.RegistrationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Solicitudes de registro, desde el lado de la administración.
 *
 * Todo este controlador es solo para administradores: lo exige SecurityConfig.
 * Las solicitudes llegan por el formulario público de {@link AuthController}.
 */
@RestController
@RequestMapping("/api/registration-requests")
@RequiredArgsConstructor
public class RegistrationRequestController {

    private final RegistrationService registrationService;

    /** Solicitudes del centro; por defecto, las que esperan decisión. */
    @GetMapping
    public ResponseEntity<PageResponse<RegistrationRequestResponse>> listar(
            @RequestParam(required = false) String status,
            Pageable pageable) {

        return ResponseEntity.ok(registrationService.listar(status, pageable));
    }

    /** Acepta la solicitud con el rol elegido: crea la cuenta y emite su invitación. */
    @PostMapping("/{id}/approve")
    public ResponseEntity<CreateUserResponse> aprobar(@PathVariable UUID id,
                                                      @Valid @RequestBody ApproveRegistrationRequest peticion) {
        return ResponseEntity.ok(registrationService.aprobar(id, peticion.role()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> rechazar(@PathVariable UUID id) {
        registrationService.rechazar(id);
        return ResponseEntity.noContent().build();
    }
}
