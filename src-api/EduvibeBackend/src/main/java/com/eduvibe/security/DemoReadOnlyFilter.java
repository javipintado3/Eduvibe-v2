package com.eduvibe.security;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.eduvibe.config.AppProperties;
import com.eduvibe.dto.common.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

/**
 * Deja las cuentas de demostración en modo solo lectura.
 *
 * Las credenciales de la demo pública están en el README, así que cualquiera puede
 * entrar con ellas. Sin este filtro, esas cuentas pueden publicar en el foro, crear
 * anuncios o cambiar la contraseña, y lo que escriban lo ve el siguiente visitante.
 *
 * Se rechaza cualquier petición que no sea de consulta (todo salvo GET, HEAD, OPTIONS
 * y TRACE), con un 403 y un mensaje que el frontend puede enseñar tal cual. La
 * excepción es entrar y salir: sin ellas la demo no se podría ni usar.
 *
 * Se decide en el servidor y no en el frontend a propósito: ocultar los botones no
 * impediría llamar a la API directamente.
 *
 * Está apagado si no se configura un dominio de demostración (desarrollo y producción
 * real, donde no hay cuentas de ese tipo). Va después de {@link JwtAuthenticationFilter}
 * porque necesita saber ya quién hace la petición.
 */
@Component
@RequiredArgsConstructor
public class DemoReadOnlyFilter extends OncePerRequestFilter {

    static final String MENSAJE = "Esta es una cuenta de demostración y solo puede consultar. "
            + "No se pueden crear ni modificar datos.";

    private static final Set<String> METODOS_DE_CONSULTA = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    /** Escrituras que sí se dejan: iniciar y cerrar sesión no cambian datos de la demo. */
    private static final Set<String> RUTAS_PERMITIDAS = Set.of("/api/auth/login", "/api/auth/logout");

    private final AppProperties propiedades;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        if (esEscrituraDeCuentaDemo(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), ErrorResponse.de(
                    HttpStatus.FORBIDDEN.value(), "Forbidden", MENSAJE, request.getRequestURI()));
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean esEscrituraDeCuentaDemo(HttpServletRequest request) {
        if (propiedades.demo() == null) {
            return false;
        }
        if (METODOS_DE_CONSULTA.contains(request.getMethod())
                || RUTAS_PERMITIDAS.contains(request.getRequestURI())) {
            return false;
        }

        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion != null
                && autenticacion.getPrincipal() instanceof AuthenticatedUser usuario
                && propiedades.demo().esSoloLectura(usuario.email());
    }
}
