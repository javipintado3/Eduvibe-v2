package com.eduvibe.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.eduvibe.exception.BadRequestException;

/**
 * Guarda archivos subidos en disco y devuelve la ruta pública para acceder a
 * ellos.
 *
 * Cada archivo se guarda con un nombre nuevo (UUID + extensión), nunca con el
 * que traía: así dos personas pueden subir "portada.jpg" el mismo día sin que
 * una pise el archivo de la otra, y el nombre original no revela nada.
 */
@Service
public class FileStorageService {

    private static final Logger LOG = LoggerFactory.getLogger(FileStorageService.class);

    private static final Set<String> EXTENSIONES_IMAGEN = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private static final Set<String> EXTENSIONES_DOCUMENTO = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "zip",
            "jpg", "jpeg", "png", "webp", "gif", "mp4");

    private static final long MAX_IMAGEN = 5L * 1024 * 1024;
    private static final long MAX_DOCUMENTO = 20L * 1024 * 1024;

    private final Path raiz;

    public FileStorageService(@Value("${app.uploads.dir}") String directorioSubidas) {
        this.raiz = Paths.get(directorioSubidas).toAbsolutePath().normalize();
        try {
            Files.createDirectories(raiz);
        } catch (IOException e) {
            throw new IllegalStateException("No se ha podido crear el directorio de subidas: " + raiz, e);
        }
    }

    /** @return la ruta pública del archivo guardado, para usar tal cual como imageUrl o fileUrl */
    public String guardar(MultipartFile archivo, TipoSubida tipo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BadRequestException("El archivo está vacío");
        }

        String extension = extensionDe(archivo.getOriginalFilename());
        Set<String> permitidas = tipo == TipoSubida.IMAGEN ? EXTENSIONES_IMAGEN : EXTENSIONES_DOCUMENTO;
        if (!permitidas.contains(extension)) {
            throw new BadRequestException("Tipo de archivo no admitido (.%s)".formatted(extension));
        }

        long maximo = tipo == TipoSubida.IMAGEN ? MAX_IMAGEN : MAX_DOCUMENTO;
        if (archivo.getSize() > maximo) {
            throw new BadRequestException("El archivo supera el tamaño máximo permitido (%d MB)"
                    .formatted(maximo / 1024 / 1024));
        }

        String nombre = UUID.randomUUID() + "." + extension;
        try {
            Files.copy(archivo.getInputStream(), raiz.resolve(nombre));
        } catch (IOException e) {
            throw new IllegalStateException("No se ha podido guardar el archivo", e);
        }

        return "/uploads/" + nombre;
    }

    /**
     * Borra un archivo subido a partir de la ruta pública que devolvió
     * {@link #guardar}. Si la ruta no es de este almacén, o el archivo ya no
     * está, no hace nada; un fallo al borrar no debe tumbar la operación que lo
     * pide (por ejemplo, borrar una cuenta).
     */
    public void borrar(String rutaPublica) {
        if (rutaPublica == null || !rutaPublica.startsWith("/uploads/")) {
            return;
        }

        Path archivo = raiz.resolve(rutaPublica.substring("/uploads/".length())).normalize();

        // Nunca se sale de la carpeta de subidas, aunque la ruta traiga "../"
        if (!archivo.startsWith(raiz)) {
            return;
        }

        try {
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            LOG.warn("No se ha podido borrar el archivo {}: {}", archivo.getFileName(), e.getMessage());
        }
    }

    private String extensionDe(String nombreOriginal) {
        if (nombreOriginal == null || !nombreOriginal.contains(".")) {
            throw new BadRequestException("El archivo no tiene extensión");
        }
        String extension = nombreOriginal.substring(nombreOriginal.lastIndexOf('.') + 1);
        return extension.toLowerCase(Locale.ROOT);
    }
}
