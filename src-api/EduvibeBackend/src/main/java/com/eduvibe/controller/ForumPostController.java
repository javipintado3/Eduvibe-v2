package com.eduvibe.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eduvibe.service.ForumService;

import lombok.RequiredArgsConstructor;

/** Un mensaje del foro. Su autor, o el profesorado de la clase como moderación, puede retirarlo. */
@RestController
@RequestMapping("/api/forum-posts")
@RequiredArgsConstructor
public class ForumPostController {

    private final ForumService forumService;

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete(@PathVariable UUID postId) {
        forumService.deleteMessage(postId);
        return ResponseEntity.noContent().build();
    }
}
