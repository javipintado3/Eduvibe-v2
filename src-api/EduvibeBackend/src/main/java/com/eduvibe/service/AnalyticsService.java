package com.eduvibe.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.dto.analytics.AssignmentAnalyticsResponse;
import com.eduvibe.dto.analytics.ClassAnalyticsResponse;
import com.eduvibe.dto.analytics.GradePointResponse;
import com.eduvibe.dto.analytics.StudentAnalyticsResponse;
import com.eduvibe.model.Assignment;
import com.eduvibe.model.Enrollment;
import com.eduvibe.model.Grade;
import com.eduvibe.model.Submission;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.EnrollmentRole;
import com.eduvibe.repository.AssignmentRepository;
import com.eduvibe.repository.EnrollmentRepository;
import com.eduvibe.repository.GradeRepository;
import com.eduvibe.repository.SubmissionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Panel de analítica del profesorado: quién ha entregado, quién no, y quién
 * acumula tareas vencidas sin entregar.
 *
 * Todo se calcula al vuelo a partir de lo que ya hay (matrículas, tareas,
 * entregas): no se guarda ningún dato nuevo, así que no hace falta migración.
 * El precio es recalcular en cada consulta, pero una clase tiene decenas de
 * alumnos y tareas, no miles, así que no compensa la complejidad de cachear.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    /** A partir de cuántas tareas ya vencidas sin entregar se marca a alguien en riesgo. */
    private static final int THRESHOLD_RISK = 2;

    private final EnrollmentRepository enrollmentRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final GradeRepository gradeRepository;
    private final ClassAccessService access;

    @Transactional(readOnly = true)
    public ClassAnalyticsResponse analyticsOf(UUID classId) {
        access.requireEditable(classId);

        List<Enrollment> alumnado = enrollmentRepository
                .findBySchoolClassIdAndRoleInClassOrderByUserNameAsc(classId, EnrollmentRole.STUDENT);
        List<Assignment> assignments = assignmentRepository.findForSchoolClass(classId);
        List<Submission> submissions = submissionRepository.findForSchoolClass(classId);

        Map<UUID, Grade> gradeBySubmission = gradesOf(submissions);

        // studentId -> assignmentId -> su entrega, solo lo que ya no es borrador:
        // un borrador todavía no cuenta como "entregado".
        Map<UUID, Map<UUID, Submission>> submittedBy = new HashMap<>();
        for (Submission submission : submissions) {
            if (submission.isDraft()) {
                continue;
            }
            submittedBy.computeIfAbsent(submission.getStudent().getId(), k -> new HashMap<>())
                    .put(submission.getAssignment().getId(), submission);
        }

        List<Assignment> overdue = assignments.stream().filter(Assignment::haVencido).toList();

        List<StudentAnalyticsResponse> students = new ArrayList<>();
        long inRisk = 0;
        for (Enrollment enrollment : alumnado) {
            StudentAnalyticsResponse fila = analyticsOfStudent(enrollment.getUser(), assignments, overdue,
                    submittedBy.getOrDefault(enrollment.getUser().getId(), Map.of()), gradeBySubmission);
            students.add(fila);
            if (fila.atRisk()) {
                inRisk++;
            }
        }

        List<AssignmentAnalyticsResponse> assignmentsResp = assignments.stream()
                .map(assignment -> analyticsOfAssignment(assignment, submittedBy, alumnado.size()))
                .toList();

        int globalPercentage = students.isEmpty() ? 100
                : (int) Math.round(students.stream().mapToInt(StudentAnalyticsResponse::completionPercent)
                        .average().orElse(100));

        return new ClassAnalyticsResponse(alumnado.size(), globalPercentage, inRisk, assignmentsResp, students);
    }

    private StudentAnalyticsResponse analyticsOfStudent(User student, List<Assignment> assignments,
                                                        List<Assignment> overdue,
                                                        Map<UUID, Submission> own,
                                                        Map<UUID, Grade> gradeBySubmission) {
        long submitted = assignments.stream().filter(t -> own.containsKey(t.getId())).count();
        long pendingOverdue = overdue.stream().filter(t -> !own.containsKey(t.getId())).count();
        int total = assignments.size();
        int percentage = total == 0 ? 100 : (int) Math.round(submitted * 100.0 / total);

        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal sumWeights = BigDecimal.ZERO;
        List<GradePointResponse> points = new ArrayList<>();

        for (Assignment assignment : assignments) {
            Submission submission = own.get(assignment.getId());
            Grade grade = submission == null ? null : gradeBySubmission.get(submission.getId());
            if (grade == null) {
                continue;
            }
            BigDecimal percentageGrade = percentageOf(grade.getScore(), assignment.getPoints());
            weightedSum = weightedSum.add(percentageGrade.multiply(assignment.getWeight()));
            sumWeights = sumWeights.add(assignment.getWeight());
            points.add(new GradePointResponse(assignment.getTitle(), grade.getGradedAt(), percentageGrade));
        }

        BigDecimal media = sumWeights.signum() == 0 ? null
                : weightedSum.divide(sumWeights, 1, RoundingMode.HALF_UP);

        return new StudentAnalyticsResponse(student.getId(), student.getName(), submitted, total - submitted,
                total, percentage, media, pendingOverdue >= THRESHOLD_RISK, points);
    }

    private AssignmentAnalyticsResponse analyticsOfAssignment(Assignment assignment,
                                                          Map<UUID, Map<UUID, Submission>> submittedBy,
                                                          long studentsOfSchoolClass) {
        long submitted = submittedBy.values().stream()
                .filter(byStudent -> byStudent.containsKey(assignment.getId()))
                .count();
        long pending = studentsOfSchoolClass - submitted;
        int percentage = studentsOfSchoolClass == 0 ? 100 : (int) Math.round(submitted * 100.0 / studentsOfSchoolClass);

        return new AssignmentAnalyticsResponse(assignment.getId(), assignment.getTitle(), assignment.getDueDate(),
                submitted, pending, percentage);
    }

    private Map<UUID, Grade> gradesOf(List<Submission> submissions) {
        List<UUID> idsGraded = submissions.stream()
                .filter(Submission::isGraded)
                .map(Submission::getId)
                .toList();

        if (idsGraded.isEmpty()) {
            return Map.of();
        }
        return gradeRepository.findForSubmissions(idsGraded).stream()
                .collect(Collectors.toMap(grade -> grade.getSubmission().getId(), grade -> grade));
    }

    private BigDecimal percentageOf(BigDecimal score, int pointsOfAssignment) {
        return score.divide(BigDecimal.valueOf(pointsOfAssignment), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }
}
