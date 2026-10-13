package com.eduvibe.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.dto.rubric.RubricScoreInput;
import com.eduvibe.dto.rubric.RubricScoreResponse;
import com.eduvibe.dto.submission.GradeRequest;
import com.eduvibe.dto.submission.GradeResponse;
import com.eduvibe.dto.submission.SubmissionToGradeResponse;
import com.eduvibe.dto.submission.SubmissionResponse;
import com.eduvibe.dto.submission.SubmitRequest;
import com.eduvibe.dto.submission.TeacherNoteRequest;
import com.eduvibe.exception.BadRequestException;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.model.Assignment;
import com.eduvibe.model.ClassGroup;
import com.eduvibe.model.ClassGroupMember;
import com.eduvibe.model.Grade;
import com.eduvibe.model.Notification;
import com.eduvibe.model.Rubric;
import com.eduvibe.model.RubricCriterion;
import com.eduvibe.model.RubricScore;
import com.eduvibe.model.Submission;
import com.eduvibe.model.User;
import com.eduvibe.repository.AssignmentRepository;
import com.eduvibe.repository.ClassGroupMemberRepository;
import com.eduvibe.repository.GradeRepository;
import com.eduvibe.repository.RubricCriterionRepository;
import com.eduvibe.repository.RubricScoreRepository;
import com.eduvibe.repository.SubmissionRepository;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import com.eduvibe.dto.common.PageResponse;

/**
 * Entregas del alumnado y su corrección.
 *
 * No depende de AssignmentService a propósito: si lo hiciera, y aquel depende
 * de este para adjuntar la entrega propia al detalle de una tarea, Spring no
 * podría construir ninguno de los dos.
 */
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final GradeRepository gradeRepository;
    private final UserRepository userRepository;
    private final ClassAccessService access;
    private final AuthService authService;
    private final NotificationService notificationService;
    private final RubricService rubricService;
    private final RubricCriterionRepository rubricCriterionRepository;
    private final RubricScoreRepository rubricScoreRepository;
    private final ClassGroupMemberRepository classGroupMemberRepository;

    /**
     * Crea o actualiza la entrega de quien está autenticado.
     *
     * Un mismo endpoint sirve para guardar el borrador y para enviar, porque es
     * literalmente el mismo gesto con una casilla distinta, y tener dos rutas
     * obligaría al cliente a decidir cuál llamar en cada pulsación.
     *
     * En una tarea grupal, lo entregado no es de quien pulsa "Entregar" sino de
     * todo su subgrupo: se guarda la misma fila (contenido, archivo, momento
     * del envío) en la entrega de cada miembro, no solo en la propia. Así el
     * resto del modelo —una entrega por alumno y tarea, una nota por entrega—
     * no necesita saber que existen los subgrupos.
     */
    @Transactional
    public SubmissionResponse saveMySubmission(UUID assignmentId, SubmitRequest request) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> NotFoundException.of("Tarea", assignmentId));

        UUID classId = assignment.getSchoolClass().getId();
        access.requireStudentOf(classId);

        AuthenticatedUser autenticado = authService.currentIdentity();
        User student = userRepository.findById(autenticado.id())
                .orElseThrow(() -> NotFoundException.of("Usuario", autenticado.id()));

        ClassGroup group = null;
        List<User> recipients = List.of(student);

        if (assignment.isGroupAssignment()) {
            ClassGroupMember membership = classGroupMemberRepository.findForStudentInSchoolClass(student.getId(), classId)
                    .orElseThrow(() -> new BadRequestException(
                            "No perteneces a ningún subgrupo de esta clase; pide al profesorado que te añada a uno."));
            group = membership.getClassGroup();
            recipients = classGroupMemberRepository.findByClassGroupId(group.getId()).stream()
                    .map(ClassGroupMember::getUser)
                    .toList();
        }

        // Mismo instante para todos los miembros: si se calculara por separado,
        // "llegó tarde" podría dar una respuesta distinta según a quién se le
        // preguntase por su propia fila.
        Instant moment = Instant.now();
        Submission miEntrega = null;

        for (User member : recipients) {
            Submission submission = submissionRepository
                    .findByAssignmentIdAndStudentId(assignmentId, member.getId())
                    .orElseGet(() -> new Submission(assignment, member));

            // Una vez corregida, ya no se puede cambiar lo entregado: la nota
            // dejaría de corresponder a lo que se calificó
            if (submission.isGraded()) {
                throw new BadRequestException("Esta entrega ya está corregida y no se puede modificar");
            }

            submission.setClassGroup(group);
            if (request.wantsToSend()) {
                submission.enviar(request.content(), request.fileUrl(), moment);
            } else {
                submission.saveDraft(request.content(), request.fileUrl());
            }
            submissionRepository.saveAndFlush(submission);

            if (member.getId().equals(student.getId())) {
                miEntrega = submission;
            }
        }

        return SubmissionResponse.of(miEntrega, null);
    }

    /** La entrega de quien consulta para una tarea, o null si aún no tiene. */
    @Transactional(readOnly = true)
    public SubmissionResponse mySubmissionIfExists(Assignment assignment) {
        AuthenticatedUser user = authService.currentIdentity();

        return submissionRepository
                .findByAssignmentIdAndStudentId(assignment.getId(), user.id())
                .map(submission -> SubmissionResponse.of(submission, gradeOf(submission)))
                .orElse(null);
    }

    /**
     * Todas las entregas de una tarea, para la pantalla de corrección.
     *
     * Las notas se traen en una sola consulta en lugar de una por entrega.
     */
    @Transactional(readOnly = true)
    public List<SubmissionResponse> listByAssignment(UUID assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> NotFoundException.of("Tarea", assignmentId));

        access.requireEditable(assignment.getSchoolClass().getId());

        List<Submission> submissions = submissionRepository.findByAssignmentIdOrderByStudentNameAsc(assignmentId);
        return withTheirGrades(submissions);
    }

    /**
     * La cola de corrección del profesorado, cruzando todas sus clases.
     *
     * Ya viene sin duplicados de tareas grupales: de eso se ocupa la consulta.
     */
    @Transactional(readOnly = true)
    public PageResponse<SubmissionToGradeResponse> toGrade(Pageable pageable) {
        AuthenticatedUser user = authService.currentIdentity();

        return PageResponse.of(
                submissionRepository.findToGradeForTeacher(user.id(), Pagination.unsorted(pageable)),
                SubmissionToGradeResponse::of);
    }

    /**
     * Pone o corrige la nota de una entrega.
     *
     * Calificar un borrador no tiene sentido: todavía no se ha entregado nada.
     * Si la tarea tiene rúbrica, la nota en bruto es la suma de lo puntuado en
     * cada criterio y no lo que venga en {@code peticion.score()}: con rúbrica,
     * la nota es una consecuencia de puntuar los criterios, no un número aparte
     * que alguien podría dejar sin corresponderse con ellos.
     *
     * En una tarea grupal, calificar cualquier entrega del subgrupo califica a
     * todo el subgrupo con la misma nota: no tendría sentido que dos compañeros
     * que entregaron lo mismo sacasen notas distintas por corregirlos por
     * separado.
     */
    @Transactional
    public SubmissionResponse grade(UUID submissionId, GradeRequest request) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> NotFoundException.of("Entrega", submissionId));

        Assignment assignment = submission.getAssignment();
        access.requireEditable(assignment.getSchoolClass().getId());

        if (submission.isDraft()) {
            throw new BadRequestException("No se puede calificar una entrega que todavía es un borrador");
        }

        Rubric rubric = rubricService.findFor(assignment.getId()).orElse(null);
        AuthenticatedUser autenticado = authService.currentIdentity();
        User grader = userRepository.findById(autenticado.id())
                .orElseThrow(() -> NotFoundException.of("Usuario", autenticado.id()));

        GradeResponse gradeOrigin = grade(submission, assignment, rubric, request, grader);

        if (submission.getClassGroup() != null) {
            List<Submission> classmates = submissionRepository.findByAssignmentIdAndClassGroupIdAndIdNot(
                    assignment.getId(), submission.getClassGroup().getId(), submission.getId());
            for (Submission classmate : classmates) {
                grade(classmate, assignment, rubric, request, grader);
            }
        }

        return SubmissionResponse.of(submission, gradeOrigin);
    }

    /** Califica una única entrega: valida el máximo, aplica la penalización y guarda la nota (y su rúbrica, si toca). */
    private GradeResponse grade(Submission submission, Assignment assignment, Rubric rubric, GradeRequest request,
                                       User grader) {
        BigDecimal rawGrade = rubric != null
                ? sumOfRubric(rubric, request.rubricScoresOrNone())
                : standaloneGrade(request);

        BigDecimal max = BigDecimal.valueOf(assignment.getPoints());
        if (rawGrade.compareTo(max) > 0) {
            throw new BadRequestException("La nota no puede pasar de " + max + ", que es lo que vale la tarea");
        }

        // El profesorado pone la nota sobre el trabajo entregado; si llegó tarde
        // y la tarea tiene penalización configurada, el descuento se aplica aquí
        // y no a mano, para que nunca dependa de que alguien se acuerde.
        boolean penaltyApplied = submission.entregadaTarde() && assignment.getLatePenaltyPercent() > 0;
        BigDecimal finalGrade = assignment.applyPenaltyIfApplies(rawGrade, submission.entregadaTarde());

        Grade grade = gradeRepository.findBySubmissionId(submission.getId())
                .orElseGet(() -> new Grade(submission, rawGrade, finalGrade, request.feedback(), grader));

        // Si ya existía, se actualiza dejando constancia de quién revisa
        grade.grade(rawGrade, finalGrade, request.feedback(), grader, penaltyApplied);
        gradeRepository.saveAndFlush(grade);

        List<RubricScoreResponse> scoresRubric = rubric != null
                ? saveScoresOfRubric(rubric, grade, request.rubricScoresOrNone())
                : List.of();

        submission.markAsGraded();
        submissionRepository.save(submission);

        notificationService.issue(submission.getStudent(), Notification.PUBLISHED_GRADE, Map.of(
                "title", assignment.getTitle(), "className", assignment.getSchoolClass().getName(),
                "assignmentId", assignment.getId().toString()));

        return GradeResponse.of(grade, scoresRubric);
    }

    private BigDecimal standaloneGrade(GradeRequest request) {
        if (request.score() == null) {
            throw new BadRequestException("La nota es obligatoria");
        }
        return request.score();
    }

    /** Suma lo puntuado en cada criterio, comprobando que cada uno respeta su propio máximo. */
    private BigDecimal sumOfRubric(Rubric rubric, List<RubricScoreInput> inputs) {
        if (inputs.isEmpty()) {
            throw new BadRequestException("Esta tarea tiene rúbrica: puntúa cada criterio");
        }

        Map<UUID, RubricCriterion> criteria = rubricCriterionRepository
                .findByRubricIdOrderBySortOrderAsc(rubric.getId())
                .stream()
                .collect(Collectors.toMap(RubricCriterion::getId, c -> c));

        if (inputs.size() != criteria.size()) {
            throw new BadRequestException("Puntúa todos los criterios de la rúbrica, ni más ni menos");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (RubricScoreInput input : inputs) {
            RubricCriterion criterion = criteria.get(input.criterionId());
            if (criterion == null) {
                throw new BadRequestException("Ese criterio no pertenece a la rúbrica de esta tarea");
            }
            if (input.points().compareTo(criterion.getMaxPoints()) > 0) {
                throw new BadRequestException(
                        "\"" + criterion.getDescription() + "\" vale como mucho " + criterion.getMaxPoints());
            }
            total = total.add(input.points());
        }
        return total;
    }

    /** Reemplaza las puntuaciones de rúbrica de esta nota por las nuevas. */
    private List<RubricScoreResponse> saveScoresOfRubric(Rubric rubric, Grade grade,
                                                                    List<RubricScoreInput> inputs) {
        rubricScoreRepository.deleteByGradeId(grade.getId());

        Map<UUID, RubricCriterion> criteria = rubricCriterionRepository
                .findByRubricIdOrderBySortOrderAsc(rubric.getId())
                .stream()
                .collect(Collectors.toMap(RubricCriterion::getId, c -> c));

        List<RubricScore> scores = new ArrayList<>();
        for (RubricScoreInput input : inputs) {
            scores.add(new RubricScore(grade, criteria.get(input.criterionId()), input.points()));
        }
        rubricScoreRepository.saveAll(scores);

        return scores.stream().map(RubricScoreResponse::of).toList();
    }

    /**
     * Deja o cambia la nota rápida del profesorado, independiente de la
     * calificación: no exige que la entrega ya esté enviada ni calificada,
     * porque su gracia es poder avisar de algo antes de llegar a ese punto.
     */
    @Transactional
    public SubmissionResponse comment(UUID submissionId, TeacherNoteRequest request) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> NotFoundException.of("Entrega", submissionId));

        access.requireEditable(submission.getAssignment().getSchoolClass().getId());

        submission.setTeacherNote(request.teacherNote());
        submissionRepository.save(submission);

        return SubmissionResponse.of(submission, gradeOf(submission));
    }

    /** Las entregas de quien consulta en una clase: su pestaña de calificaciones. */
    @Transactional(readOnly = true)
    public List<SubmissionResponse> mySubmissionsOfSchoolClass(UUID classId) {
        access.requireVisible(classId);
        AuthenticatedUser user = authService.currentIdentity();

        return withTheirGrades(submissionRepository.findForStudentInSchoolClass(user.id(), classId));
    }

    /**
     * Empareja cada entrega con su nota trayéndolas todas de golpe, en lugar de
     * consultar la nota dentro del bucle.
     */
    private List<SubmissionResponse> withTheirGrades(List<Submission> submissions) {
        if (submissions.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = submissions.stream().map(Submission::getId).toList();
        List<Grade> gradesEncontradas = gradeRepository.findForSubmissions(ids);

        Map<UUID, List<RubricScoreResponse>> rubricsByGrade = rubricsOf(gradesEncontradas);

        Map<UUID, GradeResponse> grades = new HashMap<>();
        for (Grade grade : gradesEncontradas) {
            grades.put(grade.getSubmission().getId(),
                    GradeResponse.of(grade, rubricsByGrade.getOrDefault(grade.getId(), List.of())));
        }

        return submissions.stream()
                .map(submission -> SubmissionResponse.of(submission, grades.get(submission.getId())))
                .toList();
    }

    private GradeResponse gradeOf(Submission submission) {
        return gradeRepository.findBySubmissionId(submission.getId())
                .map(grade -> GradeResponse.of(grade, rubricsOf(List.of(grade)).getOrDefault(grade.getId(), List.of())))
                .orElse(null);
    }

    /** El desglose por criterio de varias notas de golpe, en lugar de una consulta por nota. */
    private Map<UUID, List<RubricScoreResponse>> rubricsOf(List<Grade> grades) {
        if (grades.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = grades.stream().map(Grade::getId).toList();

        Map<UUID, List<RubricScoreResponse>> byGrade = new HashMap<>();
        for (RubricScore score : rubricScoreRepository.findByGradeIdIn(ids)) {
            byGrade.computeIfAbsent(score.getGrade().getId(), k -> new ArrayList<>())
                    .add(RubricScoreResponse.of(score));
        }
        return byGrade;
    }
}
