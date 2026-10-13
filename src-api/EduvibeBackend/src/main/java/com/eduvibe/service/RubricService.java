package com.eduvibe.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.dto.rubric.RubricResponse;
import com.eduvibe.dto.rubric.SaveRubricRequest;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.model.Assignment;
import com.eduvibe.model.Rubric;
import com.eduvibe.model.RubricCriterion;
import com.eduvibe.repository.AssignmentRepository;
import com.eduvibe.repository.RubricCriterionRepository;
import com.eduvibe.repository.RubricRepository;

import lombok.RequiredArgsConstructor;

/**
 * Rúbrica de una tarea: sus criterios y la puntuación máxima de cada uno.
 *
 * Se guarda siempre entera (ver {@link #save}): no hay un PATCH criterio a
 * criterio, igual que un examen no se edita pregunta a pregunta desde fuera.
 * Cambiar los criterios de una tarea que ya tiene entregas corregidas con la
 * rúbrica anterior es decisión de quien la edita, no algo que este servicio
 * intente impedir.
 */
@Service
@RequiredArgsConstructor
public class RubricService {

    private final RubricRepository rubricRepository;
    private final RubricCriterionRepository rubricCriterionRepository;
    private final AssignmentRepository assignmentRepository;
    private final ClassAccessService access;

    @Transactional(readOnly = true)
    public RubricResponse getIfExists(UUID assignmentId) {
        return rubricRepository.findByAssignmentId(assignmentId)
                .map(rubric -> RubricResponse.of(rubric,
                        rubricCriterionRepository.findByRubricIdOrderBySortOrderAsc(rubric.getId())))
                .orElse(null);
    }

    /** Crea la rúbrica de la tarea, o sustituye por completo la que ya hubiera. */
    @Transactional
    public RubricResponse save(UUID assignmentId, SaveRubricRequest request) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> NotFoundException.of("Tarea", assignmentId));

        access.requireEditable(assignment.getSchoolClass().getId());

        Rubric rubric = rubricRepository.findByAssignmentId(assignmentId)
                .orElseGet(() -> new Rubric(assignment));
        rubricRepository.saveAndFlush(rubric);

        // Se reemplazan todos los criterios: es más simple y seguro que calcular
        // un diff, y una rúbrica no tiene tantos criterios como para que borrar
        // y recrear sea un problema de rendimiento.
        rubricCriterionRepository.deleteAll(
                rubricCriterionRepository.findByRubricIdOrderBySortOrderAsc(rubric.getId()));

        List<RubricCriterion> criteria = new ArrayList<>();
        int order = 0;
        for (SaveRubricRequest.CriterionInput input : request.criteria()) {
            criteria.add(new RubricCriterion(rubric, input.description().trim(), input.maxPoints(), order++));
        }
        rubricCriterionRepository.saveAll(criteria);

        return RubricResponse.of(rubric, criteria);
    }

    @Transactional
    public void delete(UUID assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> NotFoundException.of("Tarea", assignmentId));

        access.requireEditable(assignment.getSchoolClass().getId());

        rubricRepository.findByAssignmentId(assignmentId).ifPresent(rubricRepository::delete);
    }

    /** Para el servicio de corrección: la rúbrica de la tarea si la tiene, sin exigir permisos aparte. */
    @Transactional(readOnly = true)
    public Optional<Rubric> findFor(UUID assignmentId) {
        return rubricRepository.findByAssignmentId(assignmentId);
    }
}
