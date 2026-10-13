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

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "zip",
            "jpg", "jpeg", "png", "webp", "gif", "mp4");

    private static final long MAX_IMAGE = 5L * 1024 * 1024;
    private static final long MAX_DOCUMENT = 20L * 1024 * 1024;

    private final Path root;

    public FileStorageService(@Value("${app.uploads.dir}") String uploadsDirectory) {
        this.root = Paths.get(uploadsDirectory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("No se ha podido crear el directorio de subidas: " + root, e);
        }
    }

    /** @return la ruta pública del archivo guardado, para usar tal cual como imageUrl o fileUrl */
    public String save(MultipartFile file, UploadType tipo) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("El archivo está vacío");
        }

        String extension = extensionOf(file.getOriginalFilename());
        Set<String> allowed = tipo == UploadType.IMAGE ? IMAGE_EXTENSIONS : DOCUMENT_EXTENSIONS;
        if (!allowed.contains(extension)) {
            throw new BadRequestException("Tipo de archivo no admitido (.%s)".formatted(extension));
        }

        long max = tipo == UploadType.IMAGE ? MAX_IMAGE : MAX_DOCUMENT;
        if (file.getSize() > max) {
            throw new BadRequestException("El archivo supera el tamaño máximo permitido (%d MB)"
                    .formatted(max / 1024 / 1024));
        }

        String name = UUID.randomUUID() + "." + extension;
        try {
            Files.copy(file.getInputStream(), root.resolve(name));
        } catch (IOException e) {
            throw new IllegalStateException("No se ha podido guardar el archivo", e);
        }

        return "/uploads/" + name;
    }

    /**
     * Borra un archivo subido a partir de la ruta pública que devolvió
     * {@link #save}. Si la ruta no es de este almacén, o el archivo ya no
     * está, no hace nada; un fallo al borrar no debe tumbar la operación que lo
     * pide (por ejemplo, borrar una cuenta).
     */
    public void delete(String pathPublic) {
        if (pathPublic == null || !pathPublic.startsWith("/uploads/")) {
            return;
        }

        Path file = root.resolve(pathPublic.substring("/uploads/".length())).normalize();

        // Nunca se sale de la carpeta de subidas, aunque la ruta traiga "../"
        if (!file.startsWith(root)) {
            return;
        }

        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.warn("No se ha podido borrar el archivo {}: {}", file.getFileName(), e.getMessage());
        }
    }

    private String extensionOf(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            throw new BadRequestException("El archivo no tiene extensión");
        }
        String extension = originalName.substring(originalName.lastIndexOf('.') + 1);
        return extension.toLowerCase(Locale.ROOT);
    }
}
