package com.eduvibe.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.dto.assignment.AssignmentDetailResponse;
import com.eduvibe.dto.assignment.AssignmentResponse;
import com.eduvibe.dto.assignment.SaveAssignmentRequest;
import com.eduvibe.exception.BadRequestException;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.model.Assignment;
import com.eduvibe.model.Enrollment;
import com.eduvibe.model.Notification;
import com.eduvibe.model.SchoolClass;
import com.eduvibe.model.Submission;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.EnrollmentRole;
import com.eduvibe.repository.AssignmentRepository;
import com.eduvibe.repository.EnrollmentRepository;
import com.eduvibe.repository.SubmissionRepository;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

/**
 * Tareas de una clase.
 *
 * Quién puede crearlas y editarlas lo decide {@link ClassAccessService}: hay
 * que ser profesor de esa clase concreta, o administración. Tener rol de
 * profesor en el centro no basta para poner tareas en la clase de otro.
 */
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassAccessService access;
    private final AuthService authService;
    private final SubmissionService submissionService;
    private final NotificationService notificationService;
    private final TopicService topicService;
    private final RubricService rubricService;
    private final ClassGroupService classGroupService;

    @Transactional
    public AssignmentDetailResponse create(UUID classId, SaveAssignmentRequest request) {
        SchoolClass schoolClass = access.requireEditable(classId);
        AuthenticatedUser autenticado = authService.currentIdentity();

        User author = userRepository.findById(autenticado.id())
                .orElseThrow(() -> NotFoundException.of("Usuario", autenticado.id()));

        Assignment assignment = new Assignment(schoolClass, request.title().trim(), request.description(),
                request.dueDate(), request.pointsOrDefault(), author);
        assignment.setTopic(topicService.resolveOfSchoolClass(request.topicId(), classId));
        assignment.setLatePenaltyPercent(request.penaltyOrDefault());
        assignment.setWeight(request.weightOrDefault());
        assignment.setGroupAssignment(request.isGroupOrDefault());

        assignmentRepository.saveAndFlush(assignment);
        notifyStudents(classId, schoolClass, assignment);

        return AssignmentDetailResponse.of(assignment, true, null, null, null);
    }

    /** Avisa a todo el alumnado matriculado de que hay una novedad con este título. */
    private void notifyStudents(UUID classId, SchoolClass schoolClass, Assignment assignment) {
        List<User> alumnado = enrollmentRepository
                .findBySchoolClassIdAndRoleInClassOrderByUserNameAsc(classId, EnrollmentRole.STUDENT)
                .stream().map(Enrollment::getUser).toList();

        notificationService.issueForSeveral(alumnado, Notification.NEW_ASSIGNMENT, Map.of(
                "className", schoolClass.getName(), "title", assignment.getTitle(), "assignmentId", assignment.getId().toString()));
    }

    /**
     * Trabajo de clase.
     *
     * Lo que se devuelve depende de quién mire: al alumnado, el estado de su
     * propia entrega; al profesorado, cuántas lleva recibidas cada tarea. Las
     * entregas del alumno se consultan todas juntas, no una por tarea.
     */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> list(UUID classId) {
        access.requireVisible(classId);
        AuthenticatedUser user = authService.currentIdentity();

        List<Assignment> assignments = assignmentRepository.findForSchoolClass(classId);
        if (assignments.isEmpty()) {
            return List.of();
        }

        if (access.canGradeIn(classId)) {
            return assignments.stream()
                    .map(t -> AssignmentResponse.forTeacher(t,
                            submissionRepository.countByAssignmentId(t.getId())))
                    .toList();
        }

        List<UUID> ids = assignments.stream().map(Assignment::getId).toList();

        Map<UUID, Submission> mySubmissions = new HashMap<>();
        for (Submission submission : submissionRepository.findForStudentInAssignments(user.id(), ids)) {
            mySubmissions.put(submission.getAssignment().getId(), submission);
        }

        return assignments.stream().map(assignment -> {
            Submission my = mySubmissions.get(assignment.getId());
            return AssignmentResponse.forStudent(
                    assignment,
                    my == null ? null : my.getStatus().getValue(),
                    my == null ? null : my.entregadaTarde());
        }).toList();
    }

    @Transactional(readOnly = true)
    public AssignmentDetailResponse detail(UUID assignmentId) {
        Assignment assignment = findVisible(assignmentId);
        UUID classId = assignment.getSchoolClass().getId();

        boolean puedoEditar = access.canGradeIn(classId);

        // Al profesorado no se le adjunta "su" entrega: no la tiene
        var miEntrega = puedoEditar ? null : submissionService.mySubmissionIfExists(assignment);
        var rubric = rubricService.getIfExists(assignmentId);

        AuthenticatedUser autenticado = authService.currentIdentity();
        var myGroup = (!puedoEditar && assignment.isGroupAssignment())
                ? classGroupService.myGroupInSchoolClass(autenticado.id(), classId)
                : null;

        return AssignmentDetailResponse.of(assignment, puedoEditar, miEntrega, rubric, myGroup);
    }

    @Transactional
    public AssignmentDetailResponse update(UUID assignmentId, SaveAssignmentRequest request) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> NotFoundException.of("Tarea", assignmentId));

        UUID classId = assignment.getSchoolClass().getId();
        access.requireEditable(classId);

        assignment.setTitle(request.title().trim());
        assignment.setDescription(request.description());
        assignment.setDueDate(request.dueDate());
        assignment.setPoints(request.pointsOrDefault());
        assignment.setLatePenaltyPercent(request.penaltyOrDefault());
        assignment.setWeight(request.weightOrDefault());
        assignment.setGroupAssignment(request.isGroupOrDefault());
        assignment.setTopic(topicService.resolveOfSchoolClass(request.topicId(), classId));

        assignmentRepository.save(assignment);

        return AssignmentDetailResponse.of(assignment, true, null, rubricService.getIfExists(assignmentId), null);
    }

    /**
     * Borra una tarea. Se lleva consigo sus entregas y sus notas, por las
     * cascadas de la base de datos, así que solo se permite mientras nadie haya
     * entregado nada: borrar trabajo corregido no debe ser un descuido posible.
     */
    @Transactional
    public void delete(UUID assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> NotFoundException.of("Tarea", assignmentId));

        access.requireEditable(assignment.getSchoolClass().getId());

        long submissions = submissionRepository.countByAssignmentId(assignmentId);
        if (submissions > 0) {
            throw new BadRequestException(
                    "No se puede borrar: ya hay " + submissions + " entrega(s). Edítala o retírala del tema.");
        }

        assignmentRepository.delete(assignment);
    }

    /** Busca una tarea comprobando que quien pregunta pueda ver su clase. */
    @Transactional(readOnly = true)
    public Assignment findVisible(UUID assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> NotFoundException.of("Tarea", assignmentId));

        access.requireVisible(assignment.getSchoolClass().getId());
        return assignment;
    }
}
