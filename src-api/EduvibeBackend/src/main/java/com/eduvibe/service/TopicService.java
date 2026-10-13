package com.eduvibe.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.exception.BadRequestException;
import com.eduvibe.model.Topic;
import com.eduvibe.repository.TopicRepository;

import lombok.RequiredArgsConstructor;

/**
 * Comprobaciones sobre temas compartidas por todo lo que puede colgar de uno
 * (tareas, materiales, exámenes).
 */
@Service
@RequiredArgsConstructor
public class TopicService {

    private final TopicRepository topicRepository;

    /**
     * Resuelve el tema indicado, comprobando que es de esa clase.
     *
     * Sin esta comprobación se podría colgar contenido de un tema de otra
     * clase sin más que conocer su identificador.
     */
    @Transactional(readOnly = true)
    public Topic resolveOfSchoolClass(UUID topicId, UUID classId) {
        if (topicId == null) {
            return null;
        }
        return topicRepository.findByIdAndSchoolClassId(topicId, classId)
                .orElseThrow(() -> new BadRequestException("El tema indicado no es de esta clase"));
    }
}
