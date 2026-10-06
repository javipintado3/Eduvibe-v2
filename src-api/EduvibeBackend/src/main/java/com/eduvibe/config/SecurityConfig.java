package com.eduvibe.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.eduvibe.security.JwtAuthenticationFilter;
import com.eduvibe.security.RestAccessDeniedHandler;
import com.eduvibe.security.RestAuthenticationEntryPoint;

import lombok.RequiredArgsConstructor;

/**
 * Configuración de seguridad.
 *
 * Dos principios que se mantienen a rajatabla:
 *
 *  1. La regla final es authenticated(). Todo lo que no esté declarado
 *     explícitamente como público exige sesión, de forma que añadir un endpoint
 *     nuevo no lo deja abierto por olvido.
 *
 *  2. Las reglas concretas van antes que los comodines, porque gana la primera
 *     que coincide. Poner /api/users/** antes que /api/users/{id}/invitation
 *     dejaría la segunda en código muerto.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppProperties propiedades;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS registrado dentro de la cadena de seguridad, no como WebMvcConfigurer.
     * Si se registra fuera, las peticiones preflight (OPTIONS) de los endpoints
     * protegidos se rechazan antes de llegar a Spring MVC.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(propiedades.cors().allowedOrigins());
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        configuracion.setAllowedHeaders(List.of("*"));
        configuracion.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", configuracion);
        return fuente;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // La API no usa cookies de sesión, así que no hay nada que proteger
            // frente a CSRF: el token viaja en una cabecera que el navegador no
            // adjunta automáticamente.
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(manejo -> manejo
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
            .authorizeHttpRequests(peticiones -> peticiones
                    // Spring reenvía los errores a /error. Si esa ruta exigiera
                    // autenticación, un 403 acabaría llegando al cliente como 401.
                    .requestMatchers("/error").permitAll()

                    // Acceso y alta de contraseña mediante invitación
                    .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/auth/invitations/*").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/invitations/*/accept").permitAll()

                    // Recuperar la contraseña de una cuenta existente
                    .requestMatchers(HttpMethod.POST, "/api/auth/forgot-password").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/reset-password/*").permitAll()

                    // Comprobación de estado, para el despliegue
                    .requestMatchers(HttpMethod.GET, "/api/health").permitAll()

                    // Archivos subidos (portadas, materiales, entregas): de lectura pública,
                    // ver WebConfig. Subirlos (POST /api/uploads) sigue exigiendo sesión.
                    .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()

                    // La gestión de usuarios es cosa de la administración
                    .requestMatchers("/api/users/**").hasRole("ADMIN")

                    // Crear una clase también. El resto de operaciones sobre
                    // clases dependen de la relación con esa clase concreta y
                    // no de un rol, así que las decide ClassAccessService: una
                    // regla hasRole("TEACHER") aquí dejaría a cualquier
                    // profesor del centro entrar en la clase de otro.
                    .requestMatchers(HttpMethod.POST, "/api/classes").hasRole("ADMIN")

                    .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
