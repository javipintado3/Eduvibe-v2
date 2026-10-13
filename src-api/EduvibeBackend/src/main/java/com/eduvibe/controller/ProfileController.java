package com.eduvibe.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eduvibe.dto.profile.AvatarRequest;
import com.eduvibe.dto.profile.EraseAccountRequest;
import com.eduvibe.dto.profile.PersonalDataExport;
import com.eduvibe.dto.profile.ProfileSummaryResponse;
import com.eduvibe.dto.user.UserResponse;
import com.eduvibe.service.AuthService;
import com.eduvibe.service.PersonalDataService;
import com.eduvibe.service.ProfileService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Resumen de perfil de quien está autenticado. Quién es (nombre, email, rol)
 * ya lo da {@code GET /api/auth/me}; esto es solo lo que se añade encima.
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final PersonalDataService personalDataService;
    private final AuthService authService;

    @GetMapping("/summary")
    public ResponseEntity<ProfileSummaryResponse> summary() {
        return ResponseEntity.ok(profileService.summary());
    }

    @PutMapping("/avatar")
    public ResponseEntity<UserResponse> updateAvatar(@RequestBody AvatarRequest request) {
        return ResponseEntity.ok(profileService.updateAvatar(request.url()));
    }

    /**
     * Descarga de todos los datos de la persona (derecho de acceso y
     * portabilidad). Va como adjunto para que el navegador la guarde como archivo.
     */
    @GetMapping("/export")
    public ResponseEntity<PersonalDataExport> exportData() {
        PersonalDataExport data = personalDataService.export(authService.currentIdentity().id());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"eduvibe-mis-datos.json\"")
                .body(data);
    }

    /**
     * Borra la cuenta conservando solo el expediente obligatorio. Es POST y no
     * DELETE porque lleva la confirmación en el cuerpo, y un DELETE con cuerpo
     * no lo admiten todos los clientes ni proxies.
     */
    @PostMapping("/erase")
    public ResponseEntity<Void> deleteAccount(@Valid @RequestBody EraseAccountRequest request,
                                             HttpServletRequest http) {

        personalDataService.deleteAccount(authService.currentIdentity().id(),
                request.password(), request.code(), http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
