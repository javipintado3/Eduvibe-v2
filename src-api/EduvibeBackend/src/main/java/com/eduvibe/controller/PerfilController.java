package com.eduvibe.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eduvibe.dto.perfil.AvatarRequest;
import com.eduvibe.dto.perfil.EraseAccountRequest;
import com.eduvibe.dto.perfil.PersonalDataExport;
import com.eduvibe.dto.perfil.ResumenPerfilResponse;
import com.eduvibe.dto.user.UserResponse;
import com.eduvibe.service.AuthService;
import com.eduvibe.service.DatosPersonalesService;
import com.eduvibe.service.PerfilService;

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
public class PerfilController {

    private final PerfilService perfilService;
    private final DatosPersonalesService datosPersonalesService;
    private final AuthService authService;

    @GetMapping("/summary")
    public ResponseEntity<ResumenPerfilResponse> resumen() {
        return ResponseEntity.ok(perfilService.resumen());
    }

    @PutMapping("/avatar")
    public ResponseEntity<UserResponse> actualizarAvatar(@RequestBody AvatarRequest peticion) {
        return ResponseEntity.ok(perfilService.actualizarAvatar(peticion.url()));
    }

    /**
     * Descarga de todos los datos de la persona (derecho de acceso y
     * portabilidad). Va como adjunto para que el navegador la guarde como archivo.
     */
    @GetMapping("/export")
    public ResponseEntity<PersonalDataExport> exportarDatos() {
        PersonalDataExport datos = datosPersonalesService.exportar(authService.identidadActual().id());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"eduvibe-mis-datos.json\"")
                .body(datos);
    }

    /**
     * Borra la cuenta conservando solo el expediente obligatorio. Es POST y no
     * DELETE porque lleva la confirmación en el cuerpo, y un DELETE con cuerpo
     * no lo admiten todos los clientes ni proxies.
     */
    @PostMapping("/erase")
    public ResponseEntity<Void> borrarCuenta(@Valid @RequestBody EraseAccountRequest peticion,
                                             HttpServletRequest http) {

        datosPersonalesService.borrarCuenta(authService.identidadActual().id(),
                peticion.password(), peticion.code(), http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
