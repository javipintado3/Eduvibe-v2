package com.eduvibe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.eduvibe.dto.schoolclass.CreateClassRequest;
import com.eduvibe.dto.schoolclass.CreateTopicRequest;
import com.eduvibe.dto.schoolclass.TopicResponse;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.model.Organization;
import com.eduvibe.model.SchoolClass;
import com.eduvibe.model.Topic;
import com.eduvibe.model.enums.ClassViewMode;
import com.eduvibe.model.enums.UserRole;
import com.eduvibe.repository.EnrollmentRepository;
import com.eduvibe.repository.ExamAttemptRepository;
import com.eduvibe.repository.OrganizationRepository;
import com.eduvibe.repository.SchoolClassRepository;
import com.eduvibe.repository.SubmissionRepository;
import com.eduvibe.repository.TopicRepository;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.security.AuthenticatedUser;

@ExtendWith(MockitoExtension.class)
class SchoolClassServiceTest {

    @Mock
    private SchoolClassRepository schoolClassRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ExamAttemptRepository examAttemptRepository;

    @Mock
    private ClassAccessService access;

    @Mock
    private AuthService authService;

    private SchoolClassService schoolClassService;

    private final UUID organizationId = UUID.randomUUID();
    private final Organization organization = new Organization("Centro", null);
    private final AuthenticatedUser admin =
            new AuthenticatedUser(UUID.randomUUID(), "admin@centro.es", "Admin", UserRole.ADMIN, organizationId);

    @BeforeEach
    void crearServicio() {
        schoolClassService = new SchoolClassService(
                schoolClassRepository, enrollmentRepository, topicRepository, userRepository,
                organizationRepository, submissionRepository, examAttemptRepository, access, authService);
    }

    @Nested
    @DisplayName("Crear clase")
    class Create {

        @Test
        @DisplayName("sin viewMode en la petición, nace en modo structured")
        void sinViewModeNaceStructured() {
            when(authService.currentIdentity()).thenReturn(admin);
            when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));

            schoolClassService.create(new CreateClassRequest("1º A", "Biología", "#059669", null, null));

            ArgumentCaptor<SchoolClass> captor = ArgumentCaptor.forClass(SchoolClass.class);
            verify(schoolClassRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getViewMode()).isEqualTo(ClassViewMode.STRUCTURED);
        }

        @Test
        @DisplayName("con viewMode flexible en la petición, respeta el modo pedido")
        void conViewModeFlexibleLoRespeta() {
            when(authService.currentIdentity()).thenReturn(admin);
            when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));

            schoolClassService.create(new CreateClassRequest("1º A", "Biología", "#059669", null, "flexible"));

            ArgumentCaptor<SchoolClass> captor = ArgumentCaptor.forClass(SchoolClass.class);
            verify(schoolClassRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getViewMode()).isEqualTo(ClassViewMode.FLEXIBLE);
        }
    }

    @Nested
    @DisplayName("Actualizar clase")
    class Update {

        private final UUID classId = UUID.randomUUID();
        private SchoolClass existingSchoolClass;

        @BeforeEach
        void prepararClaseExistente() {
            existingSchoolClass = new SchoolClass(organization, "1º A", "Biología", "#059669", null);
            existingSchoolClass.setViewMode(ClassViewMode.STRUCTURED);

            when(access.requireEditable(classId)).thenReturn(existingSchoolClass);

            // A partir de aquí, lo mínimo para que actualizar() pueda completar su
            // propia llamada a detalle(classId) sin explotar por un mock vacío.
            when(access.requireVisible(classId)).thenReturn(existingSchoolClass);
            when(access.canGradeIn(classId)).thenReturn(true);
            when(authService.currentIdentity()).thenReturn(admin);
            when(enrollmentRepository.findBySchoolClassIdAndRoleInClassOrderByUserNameAsc(any(), any()))
                    .thenReturn(List.of());
            when(enrollmentRepository.countBySchoolClassIdAndRoleInClass(any(), any())).thenReturn(0L);
            when(enrollmentRepository.findBySchoolClassIdAndUserId(any(), any())).thenReturn(Optional.empty());
            when(topicRepository.findBySchoolClassIdOrderBySortOrderAscTitleAsc(classId)).thenReturn(List.of());
        }

        @Test
        @DisplayName("sin viewMode en la petición, conserva el que ya tenía")
        void sinViewModeConservaElActual() {
            schoolClassService.update(classId, new CreateClassRequest("1º A", "Biología", "#059669", null, null));

            assertThat(existingSchoolClass.getViewMode()).isEqualTo(ClassViewMode.STRUCTURED);
        }

        @Test
        @DisplayName("con viewMode en la petición, lo cambia")
        void conViewModeLoCambia() {
            schoolClassService.update(classId,
                    new CreateClassRequest("1º A", "Biología", "#059669", null, "flexible"));

            assertThat(existingSchoolClass.getViewMode()).isEqualTo(ClassViewMode.FLEXIBLE);
        }
    }

    @Nested
    @DisplayName("Renombrar tema (unidad o módulo)")
    class UpdateTopic {

        private final UUID classId = UUID.randomUUID();
        private final UUID topicId = UUID.randomUUID();
        private final SchoolClass schoolClass = new SchoolClass(organization, "1º A", "Biología", "#059669", null);

        @Test
        @DisplayName("cambia el título y conserva el orden si la petición no trae uno nuevo")
        void cambiaTituloConservaOrden() {
            Topic topic = new Topic(schoolClass, "Bloque 1", 3);

            when(access.requireEditable(classId)).thenReturn(schoolClass);
            when(topicRepository.findByIdAndSchoolClassId(topicId, classId)).thenReturn(Optional.of(topic));

            TopicResponse answer = schoolClassService.updateTopic(classId, topicId,
                    new CreateTopicRequest("Bloque 1 renombrado", null));

            assertThat(answer.title()).isEqualTo("Bloque 1 renombrado");
            assertThat(topic.getSortOrder()).isEqualTo(3);
        }

        @Test
        @DisplayName("con un sortOrder nuevo en la petición, también lo actualiza")
        void actualizaOrdenSiSeIndicaUno() {
            Topic topic = new Topic(schoolClass, "Bloque 1", 3);

            when(access.requireEditable(classId)).thenReturn(schoolClass);
            when(topicRepository.findByIdAndSchoolClassId(topicId, classId)).thenReturn(Optional.of(topic));

            schoolClassService.updateTopic(classId, topicId, new CreateTopicRequest("Bloque 1", 7));

            assertThat(topic.getSortOrder()).isEqualTo(7);
        }

        @Test
        @DisplayName("con un tema que no es de esa clase, lanza NotFoundException")
        void temaDeOtraClaseLanzaNotFound() {
            when(access.requireEditable(classId)).thenReturn(schoolClass);
            when(topicRepository.findByIdAndSchoolClassId(topicId, classId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> schoolClassService.updateTopic(classId, topicId,
                    new CreateTopicRequest("Nuevo título", null)))
                    .isInstanceOf(NotFoundException.class);
        }
    }
}
