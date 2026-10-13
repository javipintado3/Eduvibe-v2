package com.eduvibe.controller;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.eduvibe.dto.common.PageResponse;
import com.eduvibe.dto.user.CreateUserRequest;
import com.eduvibe.dto.user.CreateUserResponse;
import com.eduvibe.dto.user.ImportUsersResponse;
import com.eduvibe.dto.user.InvitationResponse;
import com.eduvibe.dto.user.UpdateUserRequest;
import com.eduvibe.dto.user.UpdateUserStatusRequest;
import com.eduvibe.dto.user.UserClassResponse;
import com.eduvibe.dto.user.UserResponse;
import com.eduvibe.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Panel de administración de usuarios.
 *
 * Todo el controlador exige rol de administrador; la regla está en
 * {@link com.eduvibe.config.SecurityConfig}, en un único sitio, en lugar de
 * repetida método a método.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Listado con filtros opcionales por rol, estado, texto libre y, para el
     * selector de matriculación, exclusión de quien ya está en una clase.
     */
    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> listar(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "q") String busqueda,
            @RequestParam(required = false) UUID excludeClassId,
            @PageableDefault(size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok(userService.listar(role, status, busqueda, excludeClassId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.obtener(id));
    }

    /** Edita nombre, email y rol. La contraseña y el estado tienen sus propios flujos. */
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> actualizar(@PathVariable UUID id,
                                                    @Valid @RequestBody UpdateUserRequest peticion) {
        return ResponseEntity.ok(userService.actualizar(id, peticion));
    }

    /** Las clases en las que participa, para su ficha de administración. */
    @GetMapping("/{id}/classes")
    public ResponseEntity<List<UserClassResponse>> clases(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.clasesDe(id));
    }

    /** Exporta el listado filtrado en CSV, sin paginar. */
    @GetMapping("/export")
    public ResponseEntity<String> exportar(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "q") String busqueda) {

        String csv = userService.exportarCsv(role, status, busqueda);

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("usuarios.csv", StandardCharsets.UTF_8).build().toString())
                .body(csv);
    }

    /** Alta masiva desde un CSV con columnas name, email, role. */
    @PostMapping("/import")
    public ResponseEntity<ImportUsersResponse> importar(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userService.importar(file));
    }

    /** Alta de una cuenta. Devuelve además la invitación emitida. */
    @PostMapping
    public ResponseEntity<CreateUserResponse> crear(@Valid @RequestBody CreateUserRequest peticion) {
        CreateUserResponse creado = userService.crear(peticion);

        return ResponseEntity
                .created(URI.create("/api/users/" + creado.user().id()))
                .body(creado);
    }

    /** Activación o desactivación. Para quitar a alguien del centro, ver {@link #eliminar}. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<UserResponse> cambiarEstado(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest peticion) {

        return ResponseEntity.ok(userService.cambiarEstado(id, peticion.status()));
    }

    /**
     * Elimina a una persona del centro. Se anonimiza en lugar de borrarse, para
     * no dejar huérfano lo que escribió; ver {@link UserService#eliminar}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        userService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /** Vuelve a emitir la invitación de una cuenta pendiente. */
    @PostMapping("/{id}/invitation")
    public ResponseEntity<InvitationResponse> reenviarInvitacion(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.reenviarInvitacion(id));
    }
}
