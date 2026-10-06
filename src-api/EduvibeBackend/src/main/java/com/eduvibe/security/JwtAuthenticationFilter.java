package com.eduvibe.security;

import java.io.IOException;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.eduvibe.service.SesionService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

/**
 * Lee el token de la cabecera Authorization y, si es válido, deja al usuario
 * autenticado en el contexto de seguridad para el resto de la petición.
 *
 * El filtro nunca rechaza por sí mismo: si no hay token o no vale, simplemente
 * no autentica y deja que las reglas de autorización decidan. Así una ruta
 * pública sigue siendo accesible aunque llegue con una cabecera basura.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String CABECERA = "Authorization";
    private static final String PREFIJO = "Bearer ";

    private final JwtService jwtService;
    private final SesionService sesionService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String token = extraerToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            AuthenticatedUser usuario = jwtService.leer(token);

            if (usuario != null && sesionService.estaVigente(usuario)) {
                // Spring Security espera las autoridades de rol con el prefijo ROLE_,
                // que es lo que después permite escribir hasRole("ADMIN").
                var autoridades = List.of(
                        new SimpleGrantedAuthority("ROLE_" + usuario.role().name()));

                var autenticacion = new UsernamePasswordAuthenticationToken(usuario, null, autoridades);
                autenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(autenticacion);
            }
        }

        chain.doFilter(request, response);
    }

    private String extraerToken(HttpServletRequest request) {
        String cabecera = request.getHeader(CABECERA);
        if (cabecera == null || !cabecera.startsWith(PREFIJO)) {
            return null;
        }
        String token = cabecera.substring(PREFIJO.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
