package com.eduvibe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.eduvibe.dto.profile.PersonalDataExport;
import com.eduvibe.exception.BadRequestException;
import com.eduvibe.exception.TooManyRequestsException;
import com.eduvibe.model.GdprRequest;
import com.eduvibe.model.Organization;
import com.eduvibe.model.Submission;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.UserRole;
import com.eduvibe.model.enums.UserStatus;
import com.eduvibe.repository.AnnouncementRepository;
import com.eduvibe.repository.ClassGroupMemberRepository;
import com.eduvibe.repository.EnrollmentRepository;
import com.eduvibe.repository.ExamAttemptRepository;
import com.eduvibe.repository.ForumPostRepository;
import com.eduvibe.repository.ForumThreadRepository;
import com.eduvibe.repository.GdprRequestRepository;
import com.eduvibe.repository.GradeRepository;
import com.eduvibe.repository.InvitationRepository;
import com.eduvibe.repository.LoginAttemptRepository;
import com.eduvibe.repository.NotificationRepository;
import com.eduvibe.repository.PasswordResetRepository;
import com.eduvibe.repository.RevokedTokenRepository;
import com.eduvibe.repository.SubmissionRepository;
import com.eduvibe.repository.TotpRecoveryCodeRepository;
import com.eduvibe.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PersonalDataServiceTest {

    private static final String IP = "127.0.0.1";

    @Mock private UserRepository userRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private ClassGroupMemberRepository classGroupMemberRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private GradeRepository gradeRepository;
    @Mock private ExamAttemptRepository examAttemptRepository;
    @Mock private ForumThreadRepository forumThreadRepository;
    @Mock private ForumPostRepository forumPostRepository;
    @Mock private AnnouncementRepository announcementRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private LoginAttemptRepository loginAttemptRepository;
    @Mock private InvitationRepository invitationRepository;
    @Mock private PasswordResetRepository passwordResetRepository;
    @Mock private TotpRecoveryCodeRepository totpRecoveryCodeRepository;
    @Mock private RevokedTokenRepository revokedTokenRepository;
    @Mock private GdprRequestRepository gdprRequestRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Spy private ProtectedDemoAccounts protectedDemoAccounts = new ProtectedDemoAccounts("");
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private TwoFactorService twoFactorService;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks
    private PersonalDataService service;

    private final UUID userId = UUID.randomUUID();
    private Organization organization;
    private User user;

    @BeforeEach
    void preparar() {
        // Borrar archivos va enganchado a la confirmación de la transacción: se simula aquí
        TransactionSynchronizationManager.initSynchronization();

        organization = new Organization("Centro", null);
        organization.setId(UUID.randomUUID());
        user = newUser(UserRole.STUDENT);
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    @AfterEach
    void limpiar() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private User newUser(UserRole role) {
        User nuevo = new User(organization, "ana@centro.es", "Ana García", role);
        nuevo.setId(userId);
        nuevo.enableWith("$2a$10$hash");
        nuevo.setAvatarUrl("/uploads/foto-de-ana.jpg");
        return nuevo;
    }

    private void confirmTransaction() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    @Nested
    @DisplayName("Borrar la cuenta")
    class DeleteAccount {

        @BeforeEach
        void contrasenaCorrecta() {
            lenient().when(passwordEncoder.matches("clave", "$2a$10$hash")).thenReturn(true);
        }

        @Test
        @DisplayName("conserva el nombre y el expediente, y borra contacto, acceso y foto")
        void borraLoQueNoEsExpediente() {
            service.deleteAccount(userId, "clave", null, IP);

            assertThat(user.getName()).isEqualTo("Ana García");
            assertThat(user.getId()).isEqualTo(userId);
            assertThat(user.getEmail()).isEqualTo("borrada-" + userId + "@eduvibe.invalid");
            assertThat(user.getPasswordHash()).isNull();
            assertThat(user.getAvatarUrl()).isNull();
            assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
            assertThat(user.getTokenVersion()).isEqualTo(1);
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("elimina notificaciones, invitaciones, enlaces, códigos 2FA, tokens revocados y el historial de accesos con su IP")
        void eliminaDatosDeContactoYAcceso() {
            service.deleteAccount(userId, "clave", null, IP);

            verify(notificationRepository).deleteByUser(userId);
            verify(invitationRepository).deleteByUser(userId);
            verify(passwordResetRepository).deleteByUser(userId);
            verify(totpRecoveryCodeRepository).deleteByUser(userId);
            verify(revokedTokenRepository).deleteByUser(userId);
            // Se borra por el email que tenía, antes de sustituirlo
            verify(loginAttemptRepository).deleteAllByEmail("ana@centro.es");
        }

        @Test
        @DisplayName("vacía el texto libre del foro sin borrar los hilos de los demás")
        void anonimizaElForo() {
            service.deleteAccount(userId, "clave", null, IP);

            verify(forumPostRepository).replaceContentOf(userId, PersonalDataService.DELETED_TEXT);
            verify(forumThreadRepository).replaceTitleOf(userId, PersonalDataService.DELETED_TEXT);
        }

        @Test
        @DisplayName("borra las entregas sin calificar y sus archivos, y la foto, pero solo cuando la transacción se confirma")
        void borraBorradoresYArchivos() {
            Submission draft = new Submission();
            draft.setFileUrl("/uploads/borrador.pdf");
            when(submissionRepository.findUngradedFor(userId)).thenReturn(List.of(draft));

            service.deleteAccount(userId, "clave", null, IP);

            verify(submissionRepository).deleteAll(List.of(draft));
            // Aún no: si la transacción se deshiciera, se habrían perdido archivos de una cuenta que sigue viva
            verify(fileStorageService, never()).delete(anyString());

            confirmTransaction();

            verify(fileStorageService).delete("/uploads/foto-de-ana.jpg");
            verify(fileStorageService).delete("/uploads/borrador.pdf");
        }

        @Test
        @DisplayName("deja constancia de la solicitud de borrado")
        void registraLaSolicitud() {
            service.deleteAccount(userId, "clave", null, IP);

            ArgumentCaptor<GdprRequest> request = ArgumentCaptor.forClass(GdprRequest.class);
            verify(gdprRequestRepository).save(request.capture());
            assertThat(request.getValue().getType()).isEqualTo(GdprRequest.DELETE);
            assertThat(request.getValue().getStatus()).isEqualTo("completed");
        }

        @Test
        @DisplayName("con la contraseña incorrecta no borra nada y apunta el fallo")
        void contrasenaIncorrecta() {
            when(passwordEncoder.matches("mala", "$2a$10$hash")).thenReturn(false);

            assertThatThrownBy(() -> service.deleteAccount(userId, "mala", null, IP))
                    .isInstanceOf(BadRequestException.class);

            verify(loginAttemptService).recordFailure("ana@centro.es", IP);
            verify(notificationRepository, never()).deleteByUser(any());
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("con el 2FA activado exige un código válido")
        void conDosPasos() {
            user.setTotpEnabled(true);
            when(twoFactorService.validCode(user, "000000")).thenReturn(false);

            assertThatThrownBy(() -> service.deleteAccount(userId, "clave", "000000", IP))
                    .isInstanceOf(BadRequestException.class);

            verify(loginAttemptService).recordFailure("ana@centro.es", IP);
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("con el 2FA activado y un código válido borra la cuenta")
        void conDosPasosYCodigoValido() {
            user.setTotpEnabled(true);
            when(twoFactorService.validCode(user, "123456")).thenReturn(true);

            service.deleteAccount(userId, "clave", "123456", IP);

            assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        }

        @Test
        @DisplayName("con la cuenta bloqueada por intentos fallidos ni mira la contraseña")
        void bloqueada() {
            when(loginAttemptService.isLocked("ana@centro.es")).thenReturn(true);
            when(loginAttemptService.minutesOfLockout()).thenReturn(15L);

            assertThatThrownBy(() -> service.deleteAccount(userId, "clave", null, IP))
                    .isInstanceOf(TooManyRequestsException.class);

            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("no deja al centro sin administración: la única persona administradora activa no puede borrarse")
        void ultimaAdministracion() {
            User admin = newUser(UserRole.ADMIN);
            when(userRepository.findById(userId)).thenReturn(Optional.of(admin));
            when(userRepository.countByOrganizationIdAndRoleAndStatus(
                    organization.getId(), UserRole.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

            assertThatThrownBy(() -> service.deleteAccount(userId, "clave", null, IP))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("única persona administradora");

            assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("una persona administradora sí puede borrarse si hay otra activa")
        void adminConRelevo() {
            User admin = newUser(UserRole.ADMIN);
            when(userRepository.findById(userId)).thenReturn(Optional.of(admin));
            when(userRepository.countByOrganizationIdAndRoleAndStatus(
                    organization.getId(), UserRole.ADMIN, UserStatus.ACTIVE)).thenReturn(2L);

            service.deleteAccount(userId, "clave", null, IP);

            assertThat(admin.getStatus()).isEqualTo(UserStatus.DISABLED);
        }

        @Test
        @DisplayName("una cuenta que ya no está activa no se puede volver a borrar")
        void cuentaNoActiva() {
            user.setStatus(UserStatus.DISABLED);

            assertThatThrownBy(() -> service.deleteAccount(userId, "clave", null, IP))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    @DisplayName("Exportar los datos")
    class Export {

        @Test
        @DisplayName("devuelve el perfil y listas vacías si no hay más datos, y deja constancia")
        void sinDatos() {
            when(submissionRepository.findByStudentIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
            when(enrollmentRepository.findByUserIdOrderBySchoolClassNameAsc(userId)).thenReturn(List.of());
            when(classGroupMemberRepository.findByUserId(userId)).thenReturn(List.of());
            when(examAttemptRepository.findByStudentIdOrderByStartedAtAsc(userId)).thenReturn(List.of());
            when(forumThreadRepository.findByAuthorIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
            when(forumPostRepository.findByAuthorIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
            when(announcementRepository.findByAuthorIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
            when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged()))
                    .thenReturn(Page.empty());
            when(loginAttemptRepository.findByEmailOrderByAttemptedAtDesc("ana@centro.es")).thenReturn(List.of());

            PersonalDataExport data = service.export(userId);

            assertThat(data.profile().email()).isEqualTo("ana@centro.es");
            assertThat(data.profile().name()).isEqualTo("Ana García");
            assertThat(data.profile().organization()).isEqualTo("Centro");
            assertThat(data.submissions()).isEmpty();
            assertThat(data.notifications()).isEmpty();
            assertThat(data.accessHistory()).isEmpty();

            ArgumentCaptor<GdprRequest> request = ArgumentCaptor.forClass(GdprRequest.class);
            verify(gdprRequestRepository).save(request.capture());
            assertThat(request.getValue().getType()).isEqualTo(GdprRequest.EXPORT);
        }

        @Test
        @DisplayName("el export nunca incluye el hash de la contraseña ni el secreto del 2FA")
        void sinSecretos() {
            assertThat(PersonalDataExport.Profile.class.getRecordComponents())
                    .extracting(component -> component.getName().toLowerCase())
                    .noneMatch(name -> name.contains("password") || name.contains("secret")
                            || name.contains("hash"));
        }
    }

    @Nested
    @DisplayName("Eliminar un usuario (administración)")
    class DeleteUser {

        @Test
        @DisplayName("lo anonimiza, lo saca de sus clases y borra su acceso, sin tocar lo que escribió")
        void anonimizaYLimpia() {
            service.deleteUser(user);

            assertThat(user.getName()).isEqualTo(User.DELETED_NAME);
            assertThat(user.getEmail()).isEqualTo("borrada-" + userId + "@eduvibe.invalid");
            assertThat(user.getPasswordHash()).isNull();
            assertThat(user.getAvatarUrl()).isNull();
            assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
            assertThat(user.isDeleted()).isTrue();

            verify(enrollmentRepository).deleteByUser(userId);
            verify(classGroupMemberRepository).deleteByUser(userId);
            verify(notificationRepository).deleteByUser(userId);
            verify(revokedTokenRepository).deleteByUser(userId);
            verify(loginAttemptRepository).deleteAllByEmail("ana@centro.es");
            verify(userRepository).save(user);

            // Lo que escribió se queda: los mensajes de foro no se tocan
            verify(forumPostRepository, never()).replaceContentOf(any(), anyString());
            verify(forumThreadRepository, never()).replaceTitleOf(any(), anyString());
        }

        @Test
        @DisplayName("borra la foto de disco solo cuando la transacción se confirma")
        void borraLaFotoAlConfirmar() {
            service.deleteUser(user);
            verify(fileStorageService, never()).delete(anyString());

            confirmTransaction();
            verify(fileStorageService).delete("/uploads/foto-de-ana.jpg");
        }
    }
}
