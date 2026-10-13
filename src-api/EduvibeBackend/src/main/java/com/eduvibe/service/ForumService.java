package com.eduvibe.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.dto.forum.ForumPostResponse;
import com.eduvibe.dto.forum.ForumThreadDetailResponse;
import com.eduvibe.dto.forum.ForumThreadResponse;
import com.eduvibe.dto.forum.SaveForumPostRequest;
import com.eduvibe.dto.forum.SaveForumThreadRequest;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.model.ForumPost;
import com.eduvibe.model.ForumThread;
import com.eduvibe.model.SchoolClass;
import com.eduvibe.model.User;
import com.eduvibe.repository.ForumPostRepository;
import com.eduvibe.repository.ForumThreadRepository;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

/**
 * Foro de debate de una clase: hilos y sus mensajes.
 *
 * Cualquier persona matriculada (alumnado o profesorado) puede abrir un hilo
 * y responder; lo decide {@link ClassAccessService#requireVisible}, no
 * {@code exigirEditable} como en avisos o materiales, porque el foro es un
 * espacio de la clase entera, no algo que solo publique el profesorado.
 *
 * La moderación —borrar el mensaje de otra persona— sí es solo del
 * profesorado de la clase o administración; borrar el propio mensaje lo
 * puede hacer cualquiera, sea o no profesorado.
 */
@Service
@RequiredArgsConstructor
public class ForumService {

    private final ForumThreadRepository forumThreadRepository;
    private final ForumPostRepository forumPostRepository;
    private final UserRepository userRepository;
    private final ClassAccessService access;
    private final AuthService authService;
    private final TopicService topicService;

    @Transactional(readOnly = true)
    public List<ForumThreadResponse> list(UUID classId) {
        access.requireVisible(classId);

        List<ForumThread> threads = forumThreadRepository.findBySchoolClassId(classId);
        if (threads.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = threads.stream().map(ForumThread::getId).toList();
        Map<UUID, ForumPostRepository.StatisticsOfThread> statistics = new HashMap<>();
        for (var fila : forumPostRepository.statisticsOf(ids)) {
            statistics.put(fila.getThreadId(), fila);
        }

        return threads.stream()
                .map(thread -> {
                    var stats = statistics.get(thread.getId());
                    long total = stats != null ? stats.getTotal() : 0;
                    Instant lastPostAt = stats != null ? stats.getLastPostAt() : null;
                    return ForumThreadResponse.of(thread, total, lastPostAt);
                })
                // Los hilos con actividad más reciente van primero, como en cualquier foro
                .sorted((a, b) -> b.lastActivityAt().compareTo(a.lastActivityAt()))
                .toList();
    }

    @Transactional
    public ForumThreadDetailResponse create(UUID classId, SaveForumThreadRequest request) {
        SchoolClass schoolClass = access.requireVisible(classId);
        User author = currentUser();

        ForumThread thread = new ForumThread(schoolClass, author, request.title().trim());
        thread.setTopic(topicService.resolveOfSchoolClass(request.topicId(), classId));
        forumThreadRepository.saveAndFlush(thread);

        ForumPost firstMessage = new ForumPost(thread, author, request.content().trim());
        forumPostRepository.save(firstMessage);

        return detail(thread.getId());
    }

    @Transactional(readOnly = true)
    public ForumThreadDetailResponse detail(UUID threadId) {
        ForumThread thread = forumThreadRepository.findById(threadId)
                .orElseThrow(() -> NotFoundException.of("Hilo", threadId));

        UUID classId = thread.getSchoolClass().getId();
        access.requireVisible(classId);

        AuthenticatedUser autenticado = authService.currentIdentity();
        boolean puedoModerar = access.canGradeIn(classId);
        boolean puedoBorrarHilo = puedoModerar || thread.getAuthor().getId().equals(autenticado.id());

        List<ForumPostResponse> posts = forumPostRepository.findByThreadIdOrderByCreatedAtAsc(threadId).stream()
                .map(message -> ForumPostResponse.of(message,
                        puedoModerar || message.getAuthor().getId().equals(autenticado.id())))
                .toList();

        return ForumThreadDetailResponse.of(thread, puedoModerar, puedoBorrarHilo, posts);
    }

    @Transactional
    public ForumPostResponse reply(UUID threadId, SaveForumPostRequest request) {
        ForumThread thread = forumThreadRepository.findById(threadId)
                .orElseThrow(() -> NotFoundException.of("Hilo", threadId));

        access.requireVisible(thread.getSchoolClass().getId());
        User author = currentUser();

        ForumPost message = new ForumPost(thread, author, request.content().trim());
        // saveAndFlush, no save: createdAt lo rellena la base de datos al
        // insertar (@Generated), y se lee aquí mismo para la respuesta sin
        // ninguna consulta después que fuerce el flush.
        forumPostRepository.saveAndFlush(message);

        return ForumPostResponse.of(message, true);
    }

    @Transactional
    public void deleteThread(UUID threadId) {
        ForumThread thread = forumThreadRepository.findById(threadId)
                .orElseThrow(() -> NotFoundException.of("Hilo", threadId));

        requireAuthorOrModerator(thread.getSchoolClass().getId(), thread.getAuthor().getId());
        forumThreadRepository.delete(thread);
    }

    @Transactional
    public void deleteMessage(UUID postId) {
        ForumPost message = forumPostRepository.findById(postId)
                .orElseThrow(() -> NotFoundException.of("Mensaje", postId));

        requireAuthorOrModerator(message.getThread().getSchoolClass().getId(), message.getAuthor().getId());
        forumPostRepository.delete(message);
    }

    /** Solo el autor del mensaje/hilo, o el profesorado de la clase como moderación. */
    private void requireAuthorOrModerator(UUID classId, UUID authorId) {
        AuthenticatedUser autenticado = authService.currentIdentity();
        access.requireVisible(classId);

        boolean isAuthor = authorId.equals(autenticado.id());
        if (!isAuthor && !access.canGradeIn(classId)) {
            throw new AccessDeniedException("Solo quien lo escribió o el profesorado puede borrarlo");
        }
    }

    private User currentUser() {
        AuthenticatedUser autenticado = authService.currentIdentity();
        return userRepository.findById(autenticado.id())
                .orElseThrow(() -> NotFoundException.of("Usuario", autenticado.id()));
    }
}
