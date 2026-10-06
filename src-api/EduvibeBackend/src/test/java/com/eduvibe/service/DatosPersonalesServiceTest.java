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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.eduvibe.dto.perfil.PersonalDataExport;
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
class DatosPersonalesServiceTest {

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
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private TwoFactorService twoFactorService;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks
    private DatosPersonalesService servicio;

    private final UUID idUsuario = UUID.randomUUID();
    private Organization centro;
    private User usuario;

    @BeforeEach
    void preparar() {
        // Borrar archivos va enganchado a la confirmación de la transacción: se simula aquí
        TransactionSynchronizationManager.initSynchronization();

        centro = new Organization("Centro", null);
        centro.setId(UUID.randomUUID());
        usuario = nuevoUsuario(UserRole.STUDENT);
        lenient().when(userRepository.findById(idUsuario)).thenReturn(Optional.of(usuario));
    }

    @AfterEach
    void limpiar() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private User nuevoUsuario(UserRole rol) {
        User nuevo = new User(centro, "ana@centro.es", "Ana García", rol);
        nuevo.setId(idUsuario);
        nuevo.activarCon("$2a$10$hash");
        nuevo.setAvatarUrl("/uploads/foto-de-ana.jpg");
        return nuevo;
    }

    private void confirmarTransaccion() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    @Nested
    @DisplayName("Borrar la cuenta")
    class BorrarCuenta {

        @BeforeEach
        void contrasenaCorrecta() {
            lenient().when(passwordEncoder.matches("clave", "$2a$10$hash")).thenReturn(true);
        }

        @Test
        @DisplayName("conserva el nombre y el expediente, y borra contacto, acceso y foto")
        void borraLoQueNoEsExpediente() {
            servicio.borrarCuenta(idUsuario, "clave", null, IP);

            assertThat(usuario.getName()).isEqualTo("Ana García");
            assertThat(usuario.getId()).isEqualTo(idUsuario);
            assertThat(usuario.getEmail()).isEqualTo("borrada-" + idUsuario + "@eduvibe.invalid");
            assertThat(usuario.getPasswordHash()).isNull();
            assertThat(usuario.getAvatarUrl()).isNull();
            assertThat(usuario.getStatus()).isEqualTo(UserStatus.DISABLED);
            assertThat(usuario.getTokenVersion()).isEqualTo(1);
            verify(userRepository).save(usuario);
        }

        @Test
        @DisplayName("elimina notificaciones, invitaciones, enlaces, códigos 2FA, tokens revocados y el historial de accesos con su IP")
        void eliminaDatosDeContactoYAcceso() {
            servicio.borrarCuenta(idUsuario, "clave", null, IP);

            verify(notificationRepository).borrarDe(idUsuario);
            verify(invitationRepository).borrarDe(idUsuario);
            verify(passwordResetRepository).borrarDe(idUsuario);
            verify(totpRecoveryCodeRepository).borrarDe(idUsuario);
            verify(revokedTokenRepository).borrarDe(idUsuario);
            // Se borra por el email que tenía, antes de sustituirlo
            verify(loginAttemptRepository).borrarTodosDe("ana@centro.es");
        }

        @Test
        @DisplayName("vacía el texto libre del foro sin borrar los hilos de los demás")
        void anonimizaElForo() {
            servicio.borrarCuenta(idUsuario, "clave", null, IP);

            verify(forumPostRepository).sustituirContenidoDe(idUsuario, DatosPersonalesService.TEXTO_ELIMINADO);
            verify(forumThreadRepository).sustituirTituloDe(idUsuario, DatosPersonalesService.TEXTO_ELIMINADO);
        }

        @Test
        @DisplayName("borra las entregas sin calificar y sus archivos, y la foto, pero solo cuando la transacción se confirma")
        void borraBorradoresYArchivos() {
            Submission borrador = new Submission();
            borrador.setFileUrl("/uploads/borrador.pdf");
            when(submissionRepository.findSinCalificarDe(idUsuario)).thenReturn(List.of(borrador));

            servicio.borrarCuenta(idUsuario, "clave", null, IP);

            verify(submissionRepository).deleteAll(List.of(borrador));
            // Aún no: si la transacción se deshiciera, se habrían perdido archivos de una cuenta que sigue viva
            verify(fileStorageService, never()).borrar(anyString());

            confirmarTransaccion();

            verify(fileStorageService).borrar("/uploads/foto-de-ana.jpg");
            verify(fileStorageService).borrar("/uploads/borrador.pdf");
        }

        @Test
        @DisplayName("deja constancia de la solicitud de borrado")
        void registraLaSolicitud() {
            servicio.borrarCuenta(idUsuario, "clave", null, IP);

            ArgumentCaptor<GdprRequest> solicitud = ArgumentCaptor.forClass(GdprRequest.class);
            verify(gdprRequestRepository).save(solicitud.capture());
            assertThat(solicitud.getValue().getType()).isEqualTo(GdprRequest.BORRAR);
            assertThat(solicitud.getValue().getStatus()).isEqualTo("completed");
        }

        @Test
        @DisplayName("con la contraseña incorrecta no borra nada y apunta el fallo")
        void contrasenaIncorrecta() {
            when(passwordEncoder.matches("mala", "$2a$10$hash")).thenReturn(false);

            assertThatThrownBy(() -> servicio.borrarCuenta(idUsuario, "mala", null, IP))
                    .isInstanceOf(BadRequestException.class);

            verify(loginAttemptService).registrarFallo("ana@centro.es", IP);
            verify(notificationRepository, never()).borrarDe(any());
            assertThat(usuario.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("con el 2FA activado exige un código válido")
        void conDosPasos() {
            usuario.setTotpEnabled(true);
            when(twoFactorService.codigoValido(usuario, "000000")).thenReturn(false);

            assertThatThrownBy(() -> servicio.borrarCuenta(idUsuario, "clave", "000000", IP))
                    .isInstanceOf(BadRequestException.class);

            verify(loginAttemptService).registrarFallo("ana@centro.es", IP);
            assertThat(usuario.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("con el 2FA activado y un código válido borra la cuenta")
        void conDosPasosYCodigoValido() {
            usuario.setTotpEnabled(true);
            when(twoFactorService.codigoValido(usuario, "123456")).thenReturn(true);

            servicio.borrarCuenta(idUsuario, "clave", "123456", IP);

            assertThat(usuario.getStatus()).isEqualTo(UserStatus.DISABLED);
        }

        @Test
        @DisplayName("con la cuenta bloqueada por intentos fallidos ni mira la contraseña")
        void bloqueada() {
            when(loginAttemptService.estaBloqueado("ana@centro.es")).thenReturn(true);
            when(loginAttemptService.minutosDeBloqueo()).thenReturn(15L);

            assertThatThrownBy(() -> servicio.borrarCuenta(idUsuario, "clave", null, IP))
                    .isInstanceOf(TooManyRequestsException.class);

            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("no deja al centro sin administración: la única persona administradora activa no puede borrarse")
        void ultimaAdministracion() {
            User admin = nuevoUsuario(UserRole.ADMIN);
            when(userRepository.findById(idUsuario)).thenReturn(Optional.of(admin));
            when(userRepository.countByOrganizationIdAndRoleAndStatus(
                    centro.getId(), UserRole.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

            assertThatThrownBy(() -> servicio.borrarCuenta(idUsuario, "clave", null, IP))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("única persona administradora");

            assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("una persona administradora sí puede borrarse si hay otra activa")
        void adminConRelevo() {
            User admin = nuevoUsuario(UserRole.ADMIN);
            when(userRepository.findById(idUsuario)).thenReturn(Optional.of(admin));
            when(userRepository.countByOrganizationIdAndRoleAndStatus(
                    centro.getId(), UserRole.ADMIN, UserStatus.ACTIVE)).thenReturn(2L);

            servicio.borrarCuenta(idUsuario, "clave", null, IP);

            assertThat(admin.getStatus()).isEqualTo(UserStatus.DISABLED);
        }

        @Test
        @DisplayName("una cuenta que ya no está activa no se puede volver a borrar")
        void cuentaNoActiva() {
            usuario.setStatus(UserStatus.DISABLED);

            assertThatThrownBy(() -> servicio.borrarCuenta(idUsuario, "clave", null, IP))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    @Nested
    @DisplayName("Exportar los datos")
    class Exportar {

        @Test
        @DisplayName("devuelve el perfil y listas vacías si no hay más datos, y deja constancia")
        void sinDatos() {
            when(submissionRepository.findByStudentIdOrderByCreatedAtAsc(idUsuario)).thenReturn(List.of());
            when(enrollmentRepository.findByUserIdOrderBySchoolClassNameAsc(idUsuario)).thenReturn(List.of());
            when(classGroupMemberRepository.findByUserId(idUsuario)).thenReturn(List.of());
            when(examAttemptRepository.findByStudentIdOrderByStartedAtAsc(idUsuario)).thenReturn(List.of());
            when(forumThreadRepository.findByAuthorIdOrderByCreatedAtAsc(idUsuario)).thenReturn(List.of());
            when(forumPostRepository.findByAuthorIdOrderByCreatedAtAsc(idUsuario)).thenReturn(List.of());
            when(announcementRepository.findByAuthorIdOrderByCreatedAtAsc(idUsuario)).thenReturn(List.of());
            when(notificationRepository.findByUserIdOrderByCreatedAtDesc(idUsuario, Pageable.unpaged()))
                    .thenReturn(Page.empty());
            when(loginAttemptRepository.findByEmailOrderByAttemptedAtDesc("ana@centro.es")).thenReturn(List.of());

            PersonalDataExport datos = servicio.exportar(idUsuario);

            assertThat(datos.profile().email()).isEqualTo("ana@centro.es");
            assertThat(datos.profile().name()).isEqualTo("Ana García");
            assertThat(datos.profile().organization()).isEqualTo("Centro");
            assertThat(datos.submissions()).isEmpty();
            assertThat(datos.notifications()).isEmpty();
            assertThat(datos.accessHistory()).isEmpty();

            ArgumentCaptor<GdprRequest> solicitud = ArgumentCaptor.forClass(GdprRequest.class);
            verify(gdprRequestRepository).save(solicitud.capture());
            assertThat(solicitud.getValue().getType()).isEqualTo(GdprRequest.EXPORTAR);
        }

        @Test
        @DisplayName("el export nunca incluye el hash de la contraseña ni el secreto del 2FA")
        void sinSecretos() {
            assertThat(PersonalDataExport.Profile.class.getRecordComponents())
                    .extracting(componente -> componente.getName().toLowerCase())
                    .noneMatch(nombre -> nombre.contains("password") || nombre.contains("secret")
                            || nombre.contains("hash"));
        }
    }
}
