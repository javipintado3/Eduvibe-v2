package com.eduvibe.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eduvibe.dto.organization.OrganizationResponse;
import com.eduvibe.dto.organization.UpdateAllowedDomainRequest;
import com.eduvibe.service.OrganizationService;

import lombok.RequiredArgsConstructor;

/**
 * Ajustes del centro. Solo administración: lo exige SecurityConfig.
 */
@RestController
@RequestMapping("/api/organization")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping
    public ResponseEntity<OrganizationResponse> obtener() {
        return ResponseEntity.ok(organizationService.obtener());
    }

    @PutMapping("/allowed-domain")
    public ResponseEntity<OrganizationResponse> cambiarDominioPermitido(
            @RequestBody UpdateAllowedDomainRequest peticion) {

        return ResponseEntity.ok(organizationService.cambiarDominioPermitido(peticion.allowedDomain()));
    }
}
