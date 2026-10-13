package com.eduvibe.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.dto.resource.ResourceResponse;
import com.eduvibe.dto.resource.SaveResourceRequest;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.model.Resource;
import com.eduvibe.model.SchoolClass;
import com.eduvibe.model.Topic;
import com.eduvibe.model.enums.ResourceType;
import com.eduvibe.repository.ResourceRepository;

import lombok.RequiredArgsConstructor;

/**
 * Materiales de una clase: apuntes, enlaces, vídeos.
 *
 * Como con avisos y tareas, solo el profesorado de la clase (o administración)
 * gestiona los materiales; lo decide {@link ClassAccessService#requireEditable}.
 */
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final ClassAccessService access;
    private final TopicService topicService;

    @Transactional(readOnly = true)
    public List<ResourceResponse> list(UUID classId) {
        access.requireVisible(classId);
        boolean isTeacher = access.canGradeIn(classId);

        return resourceRepository.findBySchoolClassIdOrderBySortOrderAscTitleAsc(classId)
                .stream()
                .map(material -> ResourceResponse.of(material, isTeacher))
                .toList();
    }

    /** El orden es automático: cada material nuevo va al final de la lista de la clase. */
    @Transactional
    public ResourceResponse create(UUID classId, SaveResourceRequest request) {
        SchoolClass schoolClass = access.requireEditable(classId);
        Topic topic = topicService.resolveOfSchoolClass(request.topicId(), classId);

        int order = resourceRepository.findBySchoolClassIdOrderBySortOrderAscTitleAsc(classId).size();

        Resource material = new Resource(schoolClass, request.title().trim(), normalize(request.fileUrl()),
                typeOrNone(request.type()), order);
        material.setTopic(topic);
        material.setAvailableFrom(request.availableFrom());
        resourceRepository.saveAndFlush(material);

        return ResourceResponse.of(material, true);
    }

    @Transactional
    public ResourceResponse update(UUID resourceId, SaveResourceRequest request) {
        Resource material = resourceRepository.findById(resourceId)
                .orElseThrow(() -> NotFoundException.of("Material", resourceId));

        UUID classId = material.getSchoolClass().getId();
        access.requireEditable(classId);

        material.setTitle(request.title().trim());
        material.setFileUrl(normalize(request.fileUrl()));
        material.setType(typeOrNone(request.type()));
        material.setTopic(topicService.resolveOfSchoolClass(request.topicId(), classId));
        material.setAvailableFrom(request.availableFrom());
        resourceRepository.save(material);

        return ResourceResponse.of(material, true);
    }

    @Transactional
    public void delete(UUID resourceId) {
        Resource material = resourceRepository.findById(resourceId)
                .orElseThrow(() -> NotFoundException.of("Material", resourceId));

        access.requireEditable(material.getSchoolClass().getId());

        resourceRepository.delete(material);
    }

    private ResourceType typeOrNone(String type) {
        return (type == null || type.isBlank()) ? null : ResourceType.fromValue(type);
    }

    private String normalize(String texto) {
        return (texto == null || texto.isBlank()) ? null : texto.trim();
    }
}
