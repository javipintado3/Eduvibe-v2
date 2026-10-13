package com.eduvibe.config;

import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sirve los archivos subidos (portadas, materiales, entregas) como recursos
 * estáticos bajo /uploads/**, directamente desde disco.
 *
 * Es de lectura pública a propósito: son los mismos archivos que ya se
 * enlazan desde materiales o portadas visibles dentro de la aplicación, y
 * exigir sesión aquí solo complicaría cada &lt;img&gt; sin añadir seguridad real,
 * porque quien tiene la URL ya la consiguió a través de un endpoint protegido.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.uploads.dir}")
    private String uploadsDirectory;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registration) {
        String location = Paths.get(uploadsDirectory).toAbsolutePath().normalize().toUri().toString();
        registration.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
