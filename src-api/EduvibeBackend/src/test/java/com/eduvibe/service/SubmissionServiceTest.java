package com.eduvibe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.eduvibe.dto.common.PageResponse;
import com.eduvibe.dto.submission.SubmissionToGradeResponse;
import com.eduvibe.model.Assignment;
import com.eduvibe.model.ClassGroup;
import com.eduvibe.model.Organization;
import com.eduvibe.model.SchoolClass;
import com.eduvibe.model.Submission;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.UserRole;
import com.eduvibe.repository.SubmissionRepository;
import com.eduvibe.security.AuthenticatedUser;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private SubmissionService submissionService;

    private final Organization organization = new Organization("Centro", null);
    private final SchoolClass schoolClass = schoolClass();
    private final User teacher = user("Profe", UserRole.TEACHER);
    private final AuthenticatedUser identity =
            new AuthenticatedUser(UUID.randomUUID(), "profe@centro.es", "Profe", UserRole.TEACHER, UUID.randomUUID());

    @Test
    @DisplayName("Devuelve la página pedida con la clase y la tarea de cada entrega pendiente")
    void returnsRequestedPageWithClassAndAssignment() {
        Assignment assignment = assignment("Redacción");
        Submission ana = submission(assignment, user("Ana", UserRole.STUDENT), null);
        Submission luis = submission(assignment, user("Luis", UserRole.STUDENT), null);
        Pageable requested = PageRequest.of(0, 10);
        when(authService.currentIdentity()).thenReturn(identity);
        when(submissionRepository.findToGradeForTeacher(any(), any()))
                .thenReturn(new PageImpl<>(List.of(ana, luis), requested, 2));

        PageResponse<SubmissionToGradeResponse> result = submissionService.toGrade(requested);

        assertThat(result.contenido()).extracting(SubmissionToGradeResponse::studentName)
                .containsExactly("Ana", "Luis");
        assertThat(result.contenido().get(0).className()).isEqualTo("1º Bachillerato A");
        assertThat(result.contenido().get(0).assignmentTitle()).isEqualTo("Redacción");
        assertThat(result.contenido().get(0).groupName()).isNull();
    }

    @Test
    @DisplayName("Informa del total de entregas y de páginas, no solo de las de la página actual")
    void reportsTotalsOfTheWholeQueue() {
        Assignment assignment = assignment("Redacción");
        Submission ana = submission(assignment, user("Ana", UserRole.STUDENT), null);
        Pageable requested = PageRequest.of(1, 10);
        when(authService.currentIdentity()).thenReturn(identity);
        when(submissionRepository.findToGradeForTeacher(any(), any()))
                .thenReturn(new PageImpl<>(List.of(ana), requested, 11));

        PageResponse<SubmissionToGradeResponse> result = submissionService.toGrade(requested);

        assertThat(result.pagina()).isEqualTo(1);
        assertThat(result.tamano()).isEqualTo(10);
        assertThat(result.totalElementos()).isEqualTo(11);
        assertThat(result.totalPaginas()).isEqualTo(2);
        assertThat(result.ultima()).isTrue();
    }

    @Test
    @DisplayName("Una entrega grupal se muestra con el nombre de su subgrupo")
    void showsTheGroupNameInGroupSubmissions() {
        Assignment assignment = assignment("Proyecto en equipo");
        Submission ofTeam = submission(assignment, user("Ana", UserRole.STUDENT), group("Equipo 1"));
        Pageable requested = PageRequest.of(0, 10);
        when(authService.currentIdentity()).thenReturn(identity);
        when(submissionRepository.findToGradeForTeacher(any(), any()))
                .thenReturn(new PageImpl<>(List.of(ofTeam), requested, 1));

        PageResponse<SubmissionToGradeResponse> result = submissionService.toGrade(requested);

        assertThat(result.contenido()).extracting(SubmissionToGradeResponse::groupName)
                .containsExactly("Equipo 1");
    }

    @Test
    @DisplayName("Sin entregas pendientes devuelve una página vacía")
    void returnsEmptyPageWhenNothingPending() {
        Pageable requested = PageRequest.of(0, 10);
        when(authService.currentIdentity()).thenReturn(identity);
        when(submissionRepository.findToGradeForTeacher(any(), any()))
                .thenReturn(new PageImpl<>(List.of(), requested, 0));

        PageResponse<SubmissionToGradeResponse> result = submissionService.toGrade(requested);

        assertThat(result.contenido()).isEmpty();
        assertThat(result.totalElementos()).isZero();
    }

    @Test
    @DisplayName("Ignora el orden que pida el cliente: el de la cola lo fija la consulta")
    void ignoresClientProvidedSort() {
        Pageable withOrder = PageRequest.of(2, 5, Sort.by("propiedadQueNoExiste"));
        when(authService.currentIdentity()).thenReturn(identity);
        when(submissionRepository.findToGradeForTeacher(any(), any()))
                .thenReturn(Page.empty());

        submissionService.toGrade(withOrder);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(submissionRepository).findToGradeForTeacher(any(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        assertThat(captor.getValue().getSort().isUnsorted()).isTrue();
    }

    private SchoolClass schoolClass() {
        SchoolClass c = new SchoolClass(new Organization("Centro", null), "1º Bachillerato A", "Matemáticas", null, null);
        c.setId(UUID.randomUUID());
        return c;
    }

    private User user(String name, UserRole role) {
        User u = new User(organization, name.toLowerCase() + "@centro.es", name, role);
        u.setId(UUID.randomUUID());
        return u;
    }

    private ClassGroup group(String name) {
        ClassGroup g = new ClassGroup(schoolClass, name);
        g.setId(UUID.randomUUID());
        return g;
    }

    private Assignment assignment(String title) {
        Assignment a = new Assignment(schoolClass, title, null, null, 100, teacher);
        a.setId(UUID.randomUUID());
        return a;
    }

    private Submission submission(Assignment assignment, User student, ClassGroup group) {
        Submission s = new Submission(assignment, student);
        s.setId(UUID.randomUUID());
        s.setClassGroup(group);
        s.enviar("Trabajo", null, Instant.parse("2026-09-26T10:00:00Z"));
        return s;
    }
}
