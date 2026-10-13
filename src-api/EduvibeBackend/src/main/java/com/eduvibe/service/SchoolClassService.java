package com.eduvibe.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.dto.schoolclass.ClassDetailResponse;
import com.eduvibe.dto.schoolclass.ClassResponse;
import com.eduvibe.dto.schoolclass.CreateClassRequest;
import com.eduvibe.dto.schoolclass.CreateTopicRequest;
import com.eduvibe.dto.schoolclass.EnrollRequest;
import com.eduvibe.dto.schoolclass.MemberResponse;
import com.eduvibe.dto.schoolclass.TopicResponse;
import com.eduvibe.exception.ConflictException;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.model.Enrollment;
import com.eduvibe.model.Organization;
import com.eduvibe.model.SchoolClass;
import com.eduvibe.model.Topic;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.ClassViewMode;
import com.eduvibe.model.enums.EnrollmentRole;
import com.eduvibe.repository.EnrollmentRepository;
import com.eduvibe.repository.ExamAttemptRepository;
import com.eduvibe.repository.OrganizationRepository;
import com.eduvibe.repository.SchoolClassRepository;
import com.eduvibe.repository.SubmissionRepository;
import com.eduvibe.repository.TopicRepository;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.eduvibe.dto.common.PageResponse;

/**
 * Clases, matriculaciones y temas.
 */
@Service
@RequiredArgsConstructor
public class SchoolClassService {

    private static final String ROLE_ADMINISTRATION = "admin";

    private final SchoolClassRepository schoolClassRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final SubmissionRepository submissionRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final ClassAccessService access;
    private final AuthService authService;

    @Transactional
    public ClassDetailResponse create(CreateClassRequest request) {
        AuthenticatedUser admin = authService.currentIdentity();

        Organization organization = organizationRepository.findById(admin.organizationId())
                .orElseThrow(() -> NotFoundException.of("Organización", admin.organizationId()));

        SchoolClass schoolClass = new SchoolClass(organization, request.name().trim(),
                normalize(request.subject()), request.color(), normalize(request.imageUrl()));
        schoolClass.setViewMode(viewMode(request.viewMode()));
        schoolClassRepository.saveAndFlush(schoolClass);

        return ClassDetailResponse.of(schoolClass, ROLE_ADMINISTRATION, true, List.of(), 0, List.of());
    }

    /**
     * Las clases que ve quien consulta: las suyas, o todas las del centro si es
     * administrador.
     *
     * Lo que necesita cada tarjeta —papel, profesorado y próxima entrega— se
     * resuelve con dos consultas para el conjunto entero, no con dos por clase.
     * Con veinte clases, la diferencia es entre tres consultas y cuarenta y una.
     */
    @Transactional(readOnly = true)
    public PageResponse<ClassResponse> mySchoolClasses(String search, Pageable pageable) {
        AuthenticatedUser user = authService.currentIdentity();
        String pattern = Pagination.searchPattern(search);
        Pageable pagina = Pagination.unsorted(pageable);

        Page<SchoolClass> result = user.isAdmin()
                ? schoolClassRepository.findForOrganization(user.organizationId(), pattern, pagina)
                : schoolClassRepository.findForUser(user.id(), pattern, pagina);
        List<SchoolClass> schoolClasses = result.getContent();

        if (schoolClasses.isEmpty()) {
            return PageResponse.of(result, List.<ClassResponse>of());
        }

        List<UUID> ids = schoolClasses.stream().map(SchoolClass::getId).toList();

        // Consulta 1: la próxima entrega de cada clase
        Map<UUID, Instant> next = new HashMap<>();
        for (var fila : schoolClassRepository.findNextSubmissions(ids)) {
            next.put(fila.getClassId(), fila.getNextDueDate());
        }

        // Consulta 2: todas las matriculaciones de esas clases, de las que salen
        // tanto el profesorado como el alumnado de cada una y el papel de quien consulta
        Map<UUID, List<String>> teachersBySchoolClass = new HashMap<>();
        Map<UUID, Integer> studentsBySchoolClass = new HashMap<>();
        Map<UUID, String> myRoleBySchoolClass = new HashMap<>();

        for (Enrollment enrollment : enrollmentRepository.findBySchoolClassIdIn(ids)) {
            UUID schoolClassId = enrollment.getSchoolClass().getId();

            if (enrollment.isTeacher()) {
                teachersBySchoolClass
                        .computeIfAbsent(schoolClassId, k -> new ArrayList<>())
                        .add(enrollment.getUser().getName());
            } else {
                studentsBySchoolClass.merge(schoolClassId, 1, Integer::sum);
            }
            if (enrollment.getUser().getId().equals(user.id())) {
                myRoleBySchoolClass.put(schoolClassId, enrollment.getRoleInClass().getValue());
            }
        }

        String roleIfNotEnrolled = user.isAdmin() ? ROLE_ADMINISTRATION : null;

        return PageResponse.of(result, schoolClasses.stream()
                .map(schoolClass -> ClassResponse.of(
                        schoolClass,
                        myRoleBySchoolClass.getOrDefault(schoolClass.getId(), roleIfNotEnrolled),
                        next.get(schoolClass.getId()),
                        teachersBySchoolClass.getOrDefault(schoolClass.getId(), List.of()),
                        studentsBySchoolClass.getOrDefault(schoolClass.getId(), 0)))
                .toList());
    }

    /**
     * Borra la clase entera y todo lo que cuelga de ella (matriculaciones,
     * temas, tareas, exámenes, avisos, materiales, foros...).
     *
     * Solo administración puede hacerlo, y solo si nadie ha entregado nada
     * todavía: si ya hay entregas o intentos de examen, borrar destruiría
     * histórico académico real, y lo correcto ahí es archivar, no borrar
     * (archivar queda fuera del alcance actual).
     */
    @Transactional
    public void delete(UUID classId) {
        SchoolClass schoolClass = access.requireAdmin(classId);

        if (submissionRepository.existsByAssignmentSchoolClassId(classId)
                || examAttemptRepository.existsByExamSchoolClassId(classId)) {
            throw new ConflictException(
                    "No se puede borrar: ya hay entregas o exámenes hechos en esta clase");
        }

        schoolClassRepository.delete(schoolClass);
    }

    @Transactional(readOnly = true)
    public ClassDetailResponse detail(UUID classId) {
        SchoolClass schoolClass = access.requireVisible(classId);
        AuthenticatedUser user = authService.currentIdentity();

        List<MemberResponse> teachers = enrollmentRepository
                .findBySchoolClassIdAndRoleInClassOrderByUserNameAsc(classId, EnrollmentRole.TEACHER)
                .stream().map(MemberResponse::of).toList();

        long alumnado = enrollmentRepository.countBySchoolClassIdAndRoleInClass(classId, EnrollmentRole.STUDENT);

        List<TopicResponse> temas = topicRepository
                .findBySchoolClassIdOrderBySortOrderAscTitleAsc(classId)
                .stream().map(TopicResponse::of).toList();

        return ClassDetailResponse.of(schoolClass, myRoleIn(classId, user),
                access.canGradeIn(classId), teachers, alumnado, temas);
    }

    @Transactional
    public ClassDetailResponse update(UUID classId, CreateClassRequest request) {
        SchoolClass schoolClass = access.requireEditable(classId);

        schoolClass.setName(request.name().trim());
        schoolClass.setSubject(normalize(request.subject()));
        schoolClass.setColor(request.color());
        schoolClass.setImageUrl(normalize(request.imageUrl()));
        if (request.viewMode() != null) {
            schoolClass.setViewMode(ClassViewMode.fromValue(request.viewMode()));
        }
        schoolClassRepository.save(schoolClass);

        return detail(classId);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> members(UUID classId) {
        access.requireVisible(classId);

        return enrollmentRepository.findBySchoolClassIdOrderByRoleInClassAscUserNameAsc(classId)
                .stream().map(MemberResponse::of).toList();
    }

    /**
     * Matricula a una persona en una clase.
     *
     * Solo la administración matricula, y solo a personas de su propio centro.
     */
    @Transactional
    public MemberResponse enroll(UUID classId, EnrollRequest request) {
        AuthenticatedUser admin = authService.currentIdentity();
        SchoolClass schoolClass = access.requireEditable(classId);

        User user = userRepository.findById(request.userId())
                .filter(u -> u.getOrganization().getId().equals(admin.organizationId()))
                .orElseThrow(() -> NotFoundException.of("Usuario", request.userId()));

        if (enrollmentRepository.existsBySchoolClassIdAndUserId(classId, user.getId())) {
            throw new ConflictException(user.getName() + " ya está en esta clase");
        }

        Enrollment enrollment = new Enrollment(schoolClass, user,
                EnrollmentRole.fromValue(request.roleInClass()));
        enrollmentRepository.saveAndFlush(enrollment);

        return MemberResponse.of(enrollment);
    }

    @Transactional
    public void unenroll(UUID classId, UUID userId) {
        access.requireEditable(classId);

        Enrollment enrollment = enrollmentRepository.findBySchoolClassIdAndUserId(classId, userId)
                .orElseThrow(() -> new NotFoundException("Esa persona no está matriculada en la clase"));

        // Quitar al último profesor dejaría la clase sin nadie que pueda
        // gestionarla, salvo la administración
        if (enrollment.isTeacher()
                && enrollmentRepository.countBySchoolClassIdAndRoleInClass(classId, EnrollmentRole.TEACHER) <= 1) {
            throw new ConflictException("La clase se quedaría sin profesorado");
        }

        enrollmentRepository.delete(enrollment);
    }

    @Transactional(readOnly = true)
    public List<TopicResponse> temas(UUID classId) {
        access.requireVisible(classId);

        return topicRepository.findBySchoolClassIdOrderBySortOrderAscTitleAsc(classId)
                .stream().map(TopicResponse::of).toList();
    }

    @Transactional
    public TopicResponse createTopic(UUID classId, CreateTopicRequest request) {
        SchoolClass schoolClass = access.requireEditable(classId);

        int order = request.sortOrder() != null
                ? request.sortOrder()
                : topicRepository.findBySchoolClassIdOrderBySortOrderAscTitleAsc(classId).size() + 1;

        Topic topic = new Topic(schoolClass, request.title().trim(), order);
        topicRepository.saveAndFlush(topic);

        return TopicResponse.of(topic);
    }

    /** Solo renombra: el orden se conserva salvo que la petición traiga uno nuevo explícito. */
    @Transactional
    public TopicResponse updateTopic(UUID classId, UUID topicId, CreateTopicRequest request) {
        access.requireEditable(classId);

        Topic topic = topicRepository.findByIdAndSchoolClassId(topicId, classId)
                .orElseThrow(() -> NotFoundException.of("Tema", topicId));

        topic.setTitle(request.title().trim());
        if (request.sortOrder() != null) {
            topic.setSortOrder(request.sortOrder());
        }
        topicRepository.save(topic);

        return TopicResponse.of(topic);
    }

    /** Papel de la persona en la clase, o "admin" si solo la ve por ser administración. */
    private String myRoleIn(UUID classId, AuthenticatedUser user) {
        return enrollmentRepository.findBySchoolClassIdAndUserId(classId, user.id())
                .map(e -> e.getRoleInClass().getValue())
                .orElse(user.isAdmin() ? ROLE_ADMINISTRATION : null);
    }

    private List<String> namesOfTeachers(UUID classId) {
        return enrollmentRepository
                .findBySchoolClassIdAndRoleInClassOrderByUserNameAsc(classId, EnrollmentRole.TEACHER)
                .stream().map(e -> e.getUser().getName()).toList();
    }

    private String normalize(String texto) {
        return (texto == null || texto.isBlank()) ? null : texto.trim();
    }

    private ClassViewMode viewMode(String viewMode) {
        return viewMode != null ? ClassViewMode.fromValue(viewMode) : ClassViewMode.STRUCTURED;
    }
}
