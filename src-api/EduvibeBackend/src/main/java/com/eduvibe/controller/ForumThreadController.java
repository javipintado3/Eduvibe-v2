package com.eduvibe.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eduvibe.dto.forum.ForumPostResponse;
import com.eduvibe.dto.forum.ForumThreadDetailResponse;
import com.eduvibe.dto.forum.SaveForumPostRequest;
import com.eduvibe.service.ForumService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Un hilo del foro ya abierto: se crea colgado de su clase
 * ({@code POST /api/classes/{id}/forum-threads}); a partir de ahí se consulta,
 * se le responde o se retira por su propio identificador.
 */
@RestController
@RequestMapping("/api/forum-threads")
@RequiredArgsConstructor
public class ForumThreadController {

    private final ForumService forumService;

    @GetMapping("/{threadId}")
    public ResponseEntity<ForumThreadDetailResponse> detail(@PathVariable UUID threadId) {
        return ResponseEntity.ok(forumService.detail(threadId));
    }

    @PostMapping("/{threadId}/posts")
    public ResponseEntity<ForumPostResponse> reply(@PathVariable UUID threadId,
                                                        @Valid @RequestBody SaveForumPostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(forumService.reply(threadId, request));
    }

    /** Borra el hilo entero, con todos sus mensajes. Su autor, o el profesorado como moderación. */
    @DeleteMapping("/{threadId}")
    public ResponseEntity<Void> delete(@PathVariable UUID threadId) {
        forumService.deleteThread(threadId);
        return ResponseEntity.noContent().build();
    }
}
