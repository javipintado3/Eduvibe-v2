package com.eduvibe.controller;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.eduvibe.service.FileStorageService;
import com.eduvibe.service.UploadType;

import lombok.RequiredArgsConstructor;

/**
 * Subida de un archivo suelto: portada de clase, material o entrega.
 *
 * No hace falta saber para qué se va a usar —eso lo decide quien llama
 * después, al mandar la URL que devuelve esto como imageUrl o fileUrl del
 * recurso que corresponda—, así que este endpoint es el mismo para los tres
 * casos. Cualquier persona autenticada puede subir: quien puede o no usar la
 * URL resultante ya lo decide el endpoint donde se guarda.
 */
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final FileStorageService storage;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(name = "tipo", required = false) String tipo) {
        String url = storage.save(file, UploadType.fromValue(tipo));
        return ResponseEntity.ok(Map.of("url", url));
    }
}
