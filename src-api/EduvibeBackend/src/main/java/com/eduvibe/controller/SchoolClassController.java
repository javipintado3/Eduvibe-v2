package com.eduvibe.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eduvibe.dto.analytics.ClassAnalyticsResponse;
import com.eduvibe.dto.announcement.AnnouncementResponse;
import com.eduvibe.dto.announcement.SaveAnnouncementRequest;
import com.eduvibe.dto.assignment.AssignmentDetailResponse;
import com.eduvibe.dto.assignment.AssignmentResponse;
import com.eduvibe.dto.assignment.SaveAssignmentRequest;
import com.eduvibe.dto.classgroup.ClassGroupResponse;
import com.eduvibe.dto.classgroup.SaveClassGroupRequest;
import com.eduvibe.dto.exam.BankQuestionResponse;
import com.eduvibe.dto.exam.ExamDetailResponse;
import com.eduvibe.dto.exam.ExamResponse;
import com.eduvibe.dto.exam.SaveExamRequest;
import com.eduvibe.dto.forum.ForumThreadDetailResponse;
import com.eduvibe.dto.forum.ForumThreadResponse;
import com.eduvibe.dto.forum.SaveForumThreadRequest;
import com.eduvibe.dto.schoolclass.ClassDetailResponse;
import com.eduvibe.dto.schoolclass.ClassResponse;
import com.eduvibe.dto.schoolclass.CreateClassRequest;
import com.eduvibe.dto.schoolclass.CreateTopicRequest;
import com.eduvibe.dto.schoolclass.EnrollRequest;
import com.eduvibe.dto.schoolclass.MemberResponse;
import com.eduvibe.dto.resource.ResourceResponse;
import com.eduvibe.dto.resource.SaveResourceRequest;
import com.eduvibe.dto.schoolclass.TopicResponse;
import com.eduvibe.dto.submission.SubmissionResponse;
import com.eduvibe.service.AnalyticsService;
import com.eduvibe.service.AnnouncementService;
import com.eduvibe.service.AssignmentService;
import com.eduvibe.service.ClassGroupService;
import com.eduvibe.service.ExamService;
import com.eduvibe.service.ForumService;
import com.eduvibe.service.ResourceService;
import com.eduvibe.service.SchoolClassService;
import com.eduvibe.service.SubmissionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import com.eduvibe.dto.common.PageResponse;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Clases y todo lo que cuelga de una: personas, temas, tareas y calificaciones.
 *
 * Los permisos no se declaran aquí sino en los servicios, a través de
 * {@link com.eduvibe.service.ClassAccessService}, porque dependen de la
 * relación entre la persona y esa clase concreta y no solo de su rol: una regla
 * como hasRole('TEACHER') dejaría a cualquier profesor del centro poner tareas
 * en la clase de otro.
 */
@RestController
@RequestMapping("/api/classes")
@RequiredArgsConstructor
public class SchoolClassController {

    private final SchoolClassService schoolClassService;
    private final AssignmentService assignmentService;
    private final SubmissionService submissionService;
    private final AnnouncementService announcementService;
    private final ResourceService resourceService;
    private final ExamService examService;
    private final ForumService forumService;
    private final ClassGroupService classGroupService;
    private final AnalyticsService analyticsService;

    // ---------------------------------------------------------------- clases

    /** Panel principal: las clases de quien consulta. */
    @GetMapping
    public ResponseEntity<PageResponse<ClassResponse>> mySchoolClasses(
            @RequestParam(required = false, name = "q") String search, Pageable pageable) {
        return ResponseEntity.ok(schoolClassService.mySchoolClasses(search, pageable));
    }

    @PostMapping
    public ResponseEntity<ClassDetailResponse> create(@Valid @RequestBody CreateClassRequest request) {
        ClassDetailResponse created = schoolClassService.create(request);
        return ResponseEntity.created(URI.create("/api/classes/" + created.id())).body(created);
    }

    @GetMapping("/{classId}")
    public ResponseEntity<ClassDetailResponse> detail(@PathVariable UUID classId) {
        return ResponseEntity.ok(schoolClassService.detail(classId));
    }

    @PutMapping("/{classId}")
    public ResponseEntity<ClassDetailResponse> update(@PathVariable UUID classId,
                                                          @Valid @RequestBody CreateClassRequest request) {
        return ResponseEntity.ok(schoolClassService.update(classId, request));
    }

    /** Borra la clase entera. Solo administración, y solo si nadie ha entregado nada todavía. */
    @DeleteMapping("/{classId}")
    public ResponseEntity<Void> delete(@PathVariable UUID classId) {
        schoolClassService.delete(classId);
        return ResponseEntity.noContent().build();
    }

    // ----------------------------------------------------------- matrículas

    @GetMapping("/{classId}/members")
    public ResponseEntity<List<MemberResponse>> members(@PathVariable UUID classId) {
        return ResponseEntity.ok(schoolClassService.members(classId));
    }

    @PostMapping("/{classId}/members")
    public ResponseEntity<MemberResponse> enroll(@PathVariable UUID classId,
                                                     @Valid @RequestBody EnrollRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(schoolClassService.enroll(classId, request));
    }

    @DeleteMapping("/{classId}/members/{userId}")
    public ResponseEntity<Void> unenroll(@PathVariable UUID classId, @PathVariable UUID userId) {
        schoolClassService.unenroll(classId, userId);
        return ResponseEntity.noContent().build();
    }

    // ----------------------------------------------------------------- temas

    @GetMapping("/{classId}/topics")
    public ResponseEntity<List<TopicResponse>> temas(@PathVariable UUID classId) {
        return ResponseEntity.ok(schoolClassService.temas(classId));
    }

    @PostMapping("/{classId}/topics")
    public ResponseEntity<TopicResponse> createTopic(@PathVariable UUID classId,
                                                   @Valid @RequestBody CreateTopicRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(schoolClassService.createTopic(classId, request));
    }

    @PutMapping("/{classId}/topics/{topicId}")
    public ResponseEntity<TopicResponse> updateTopic(@PathVariable UUID classId, @PathVariable UUID topicId,
                                                        @Valid @RequestBody CreateTopicRequest request) {
        return ResponseEntity.ok(schoolClassService.updateTopic(classId, topicId, request));
    }

    // ---------------------------------------------------------------- tareas

    /** Trabajo de clase. Lo que se devuelve depende de quién pregunte. */
    @GetMapping("/{classId}/assignments")
    public ResponseEntity<List<AssignmentResponse>> assignments(@PathVariable UUID classId) {
        return ResponseEntity.ok(assignmentService.list(classId));
    }

    @PostMapping("/{classId}/assignments")
    public ResponseEntity<AssignmentDetailResponse> createAssignment(@PathVariable UUID classId,
                                                               @Valid @RequestBody SaveAssignmentRequest request) {
        AssignmentDetailResponse created = assignmentService.create(classId, request);
        return ResponseEntity.created(URI.create("/api/assignments/" + created.id())).body(created);
    }

    // -------------------------------------------------------- calificaciones

    /** Pestaña de calificaciones: las entregas propias en esta clase. */
    @GetMapping("/{classId}/my-submissions")
    public ResponseEntity<List<SubmissionResponse>> mySubmissions(@PathVariable UUID classId) {
        return ResponseEntity.ok(submissionService.mySubmissionsOfSchoolClass(classId));
    }

    // ---------------------------------------------------------------- avisos

    /** Muro de la clase: fijados primero, luego lo más reciente. */
    @GetMapping("/{classId}/announcements")
    public ResponseEntity<PageResponse<AnnouncementResponse>> announcements(@PathVariable UUID classId, Pageable pageable) {
        return ResponseEntity.ok(announcementService.list(classId, pageable));
    }

    @PostMapping("/{classId}/announcements")
    public ResponseEntity<AnnouncementResponse> publishAnnouncement(@PathVariable UUID classId,
                                                               @Valid @RequestBody SaveAnnouncementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(announcementService.create(classId, request));
    }

    // ------------------------------------------------------------ materiales

    @GetMapping("/{classId}/resources")
    public ResponseEntity<List<ResourceResponse>> resources(@PathVariable UUID classId) {
        return ResponseEntity.ok(resourceService.list(classId));
    }

    @PostMapping("/{classId}/resources")
    public ResponseEntity<ResourceResponse> addMaterial(@PathVariable UUID classId,
                                                            @Valid @RequestBody SaveResourceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(resourceService.create(classId, request));
    }

    // -------------------------------------------------------------- exámenes

    /** Exámenes de la clase. Lo que se devuelve depende de quién pregunte. */
    @GetMapping("/{classId}/exams")
    public ResponseEntity<List<ExamResponse>> exams(@PathVariable UUID classId) {
        return ResponseEntity.ok(examService.list(classId));
    }

    @PostMapping("/{classId}/exams")
    public ResponseEntity<ExamDetailResponse> createExam(@PathVariable UUID classId,
                                                          @Valid @RequestBody SaveExamRequest request) {
        ExamDetailResponse creado = examService.create(classId, request);
        return ResponseEntity.created(URI.create("/api/exams/" + creado.id())).body(creado);
    }

    /** Preguntas ya usadas en algún examen de la clase, para reutilizarlas en uno nuevo. Solo profesorado. */
    @GetMapping("/{classId}/exams/question-bank")
    public ResponseEntity<List<BankQuestionResponse>> questionBank(@PathVariable UUID classId) {
        return ResponseEntity.ok(examService.listBankQuestions(classId));
    }

    // ----------------------------------------------------------------- foro

    /** Hilos del foro, con más actividad reciente primero. */
    @GetMapping("/{classId}/forum-threads")
    public ResponseEntity<List<ForumThreadResponse>> threadsOfForum(@PathVariable UUID classId) {
        return ResponseEntity.ok(forumService.list(classId));
    }

    @PostMapping("/{classId}/forum-threads")
    public ResponseEntity<ForumThreadDetailResponse> openThread(@PathVariable UUID classId,
                                                               @Valid @RequestBody SaveForumThreadRequest request) {
        ForumThreadDetailResponse creado = forumService.create(classId, request);
        return ResponseEntity.created(URI.create("/api/forum-threads/" + creado.id())).body(creado);
    }

    // ------------------------------------------------------------ subgrupos

    @GetMapping("/{classId}/groups")
    public ResponseEntity<List<ClassGroupResponse>> subgroups(@PathVariable UUID classId) {
        return ResponseEntity.ok(classGroupService.list(classId));
    }

    // ---------------------------------------------------------------- analítica

    /** Panel del profesorado: quién ha entregado, quién no, y quién está en riesgo. */
    @GetMapping("/{classId}/analytics")
    public ResponseEntity<ClassAnalyticsResponse> analytics(@PathVariable UUID classId) {
        return ResponseEntity.ok(analyticsService.analyticsOf(classId));
    }

    @PostMapping("/{classId}/groups")
    public ResponseEntity<ClassGroupResponse> createSubgroup(@PathVariable UUID classId,
                                                            @Valid @RequestBody SaveClassGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(classGroupService.create(classId, request));
    }
}
