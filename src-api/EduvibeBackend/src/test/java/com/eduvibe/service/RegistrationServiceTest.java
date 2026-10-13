package com.eduvibe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
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

import com.eduvibe.config.AppProperties;
import com.eduvibe.dto.auth.RegisterRequest;
import com.eduvibe.dto.user.CreateUserResponse;
import com.eduvibe.dto.user.InvitationResponse;
import com.eduvibe.exception.BadRequestException;
import com.eduvibe.exception.ConflictException;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.exception.TooManyRequestsException;
import com.eduvibe.model.Notification;
import com.eduvibe.model.Organization;
import com.eduvibe.model.RegistrationRequest;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.RegistrationStatus;
import com.eduvibe.model.enums.UserRole;
import com.eduvibe.repository.OrganizationRepository;
import com.eduvibe.repository.RegistrationRequestRepository;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.security.AuthenticatedUser;
import com.eduvibe.util.Tokens;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    private static final String EMAIL = "ana@centro.es";
    private static final String IP = "203.0.113.7";

    @Mock
    private RegistrationRequestRepository requestRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Mock
    private InvitationService invitationService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private MailService mailService;

    @Mock
    private AuthService authService;

    private RegistrationService servicio;

    private Organization centro;

    @BeforeEach
    void crearServicio() {
        AppProperties propiedades = new AppProperties(null, null, "http://localhost:4200", null, null, null,
                new AppProperties.Registration(24, 5, 100, false));
        servicio = new RegistrationService(requestRepository, organizationRepository, userRepository,
                userService, invitationService, notificationService, mailService, authService, propiedades);

        centro = new Organization("Centro", "centro.es");
        centro.setId(UUID.randomUUID());
    }

    private RegisterRequest peticion(String email) {
        return new RegisterRequest("Ana", email, null, "");
    }

    private RegistrationRequest solicitudSinVerificar() {
        RegistrationRequest solicitud = new RegistrationRequest(centro, EMAIL, "Ana", null, IP);
        solicitud.emitirVerificacion(Tokens.hashear("token"), Instant.now().plusSeconds(3600));
        return solicitud;
    }

    private RegistrationRequest solicitudPendiente() {
        RegistrationRequest solicitud = solicitudSinVerificar();
        solicitud.setId(UUID.randomUUID());
        solicitud.marcarComoVerificada();
        return solicitud;
    }

    private AuthenticatedUser administrador() {
        return new AuthenticatedUser(UUID.randomUUID(), "admin@centro.es", "Admin", UserRole.ADMIN, centro.getId());
    }

    @Nested
    @DisplayName("Solicitar")
    class Solicitar {

        @Test
        @DisplayName("con un email nuevo de un dominio conocido guarda solo el hash del token y envía el enlace con el token en claro")
        void emailNuevo() {
            when(organizationRepository.findByAllowedDomain("centro.es")).thenReturn(Optional.of(centro));
            when(requestRepository.findFirstByEmailAndStatusIn(eq(EMAIL), anyCollection())).thenReturn(Optional.empty());

            servicio.solicitar(peticion("  Ana@Centro.ES "), IP);

            ArgumentCaptor<RegistrationRequest> guardada = ArgumentCaptor.forClass(RegistrationRequest.class);
            verify(requestRepository).save(guardada.capture());
            ArgumentCaptor<String> enlace = ArgumentCaptor.forClass(String.class);
            verify(mailService).enviarVerificacionDeRegistro(eq("Ana"), eq(EMAIL), enlace.capture());

            String token = enlace.getValue().substring(enlace.getValue().lastIndexOf('/') + 1);
            assertThat(enlace.getValue()).startsWith("http://localhost:4200/verificar-registro/");
            assertThat(guardada.getValue().getTokenHash()).isEqualTo(Tokens.hashear(token));
            assertThat(guardada.getValue().getTokenHash()).isNotEqualTo(token);
            assertThat(guardada.getValue().getStatus()).isEqualTo(RegistrationStatus.UNVERIFIED);
            assertThat(guardada.getValue().getIpAddress()).isEqualTo(IP);
        }

        @Test
        @DisplayName("guarda el mensaje sin espacios sobrantes, y uno en blanco se guarda como ausente")
        void mensaje() {
            when(organizationRepository.findByAllowedDomain("centro.es")).thenReturn(Optional.of(centro));
            when(requestRepository.findFirstByEmailAndStatusIn(anyString(), anyCollection()))
                    .thenReturn(Optional.empty());

            servicio.solicitar(new RegisterRequest("Ana", EMAIL, "  Soy de 2.º B  ", ""), IP);
            servicio.solicitar(new RegisterRequest("Bea", "bea@centro.es", "   ", ""), IP);

            ArgumentCaptor<RegistrationRequest> guardadas = ArgumentCaptor.forClass(RegistrationRequest.class);
            verify(requestRepository, org.mockito.Mockito.times(2)).save(guardadas.capture());
            assertThat(guardadas.getAllValues().get(0).getMessage()).isEqualTo("Soy de 2.º B");
            assertThat(guardadas.getAllValues().get(1).getMessage()).isNull();
        }

        @Test
        @DisplayName("si el dominio no corresponde a ningún centro no guarda ni envía nada, y no da error")
        void dominioDesconocido() {
            when(organizationRepository.findByAllowedDomain("otro.com")).thenReturn(Optional.empty());

            servicio.solicitar(peticion("ana@otro.com"), IP);

            verify(requestRepository, never()).save(any());
            verifyNoInteractions(mailService);
        }

        @Test
        @DisplayName("si ya existe una cuenta con ese email no guarda ni envía nada, y no da error")
        void cuentaExistente() {
            when(organizationRepository.findByAllowedDomain("centro.es")).thenReturn(Optional.of(centro));
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            servicio.solicitar(peticion(EMAIL), IP);

            verify(requestRepository, never()).save(any());
            verifyNoInteractions(mailService);
        }

        @Test
        @DisplayName("si se rechazó a ese email hace poco, lo ignora sin avisar")
        void rechazadaHacePoco() {
            when(organizationRepository.findByAllowedDomain("centro.es")).thenReturn(Optional.of(centro));
            when(requestRepository.existsByEmailAndStatusAndReviewedAtAfter(
                    eq(EMAIL), eq(RegistrationStatus.REJECTED), any())).thenReturn(true);

            servicio.solicitar(peticion(EMAIL), IP);

            verify(requestRepository, never()).save(any());
            verifyNoInteractions(mailService);
        }

        @Test
        @DisplayName("si el campo cebo viene relleno lo descarta sin tocar nada")
        void bot() {
            servicio.solicitar(new RegisterRequest("Bot", EMAIL, null, "http://spam.example"), IP);

            verifyNoInteractions(requestRepository, organizationRepository, userRepository, mailService);
        }

        @Test
        @DisplayName("si la IP supera el límite por hora lanza 429")
        void limitePorIp() {
            when(requestRepository.countByIpAddressAndCreatedAtAfter(eq(IP), any())).thenReturn(5L);

            assertThatThrownBy(() -> servicio.solicitar(peticion(EMAIL), IP))
                    .isInstanceOf(TooManyRequestsException.class);
            verify(requestRepository, never()).save(any());
        }

        @Test
        @DisplayName("si la plataforma entera supera el tope por hora lanza 429, aunque la IP esté limpia")
        void limiteGlobal() {
            when(requestRepository.countByCreatedAtAfter(any())).thenReturn(100L);

            assertThatThrownBy(() -> servicio.solicitar(peticion(EMAIL), IP))
                    .isInstanceOf(TooManyRequestsException.class);
        }

        @Test
        @DisplayName("si ya hay una solicitud sin verificar y se pidió el enlace hace poco, no envía otro")
        void reenvioDemasiadoPronto() {
            RegistrationRequest abierta = solicitudSinVerificar();
            abierta.setUpdatedAt(Instant.now().minusSeconds(30));
            when(organizationRepository.findByAllowedDomain("centro.es")).thenReturn(Optional.of(centro));
            when(requestRepository.findFirstByEmailAndStatusIn(eq(EMAIL), anyCollection()))
                    .thenReturn(Optional.of(abierta));

            servicio.solicitar(peticion(EMAIL), IP);

            verifyNoInteractions(mailService);
        }

        @Test
        @DisplayName("si ya hay una solicitud sin verificar y pasó la espera, emite un token nuevo que sustituye al anterior")
        void reenvioPasadaLaEspera() {
            RegistrationRequest abierta = solicitudSinVerificar();
            String hashAnterior = abierta.getTokenHash();
            abierta.setUpdatedAt(Instant.now().minusSeconds(3600));
            when(organizationRepository.findByAllowedDomain("centro.es")).thenReturn(Optional.of(centro));
            when(requestRepository.findFirstByEmailAndStatusIn(eq(EMAIL), anyCollection()))
                    .thenReturn(Optional.of(abierta));

            servicio.solicitar(peticion(EMAIL), IP);

            verify(mailService).enviarVerificacionDeRegistro(anyString(), eq(EMAIL), anyString());
            assertThat(abierta.getTokenHash()).isNotEqualTo(hashAnterior);
        }

        @Test
        @DisplayName("si la solicitud ya está pendiente de aprobar, no envía nada")
        void yaPendiente() {
            RegistrationRequest abierta = solicitudPendiente();
            when(organizationRepository.findByAllowedDomain("centro.es")).thenReturn(Optional.of(centro));
            when(requestRepository.findFirstByEmailAndStatusIn(eq(EMAIL), anyCollection()))
                    .thenReturn(Optional.of(abierta));

            servicio.solicitar(peticion(EMAIL), IP);

            verifyNoInteractions(mailService);
        }
    }

    @Nested
    @DisplayName("Verificar")
    class Verificar {

        @Test
        @DisplayName("con un token válido pasa a pendiente, invalida el token y avisa a los administradores")
        void tokenValido() {
            RegistrationRequest solicitud = solicitudSinVerificar();
            User admin = new User(centro, "admin@centro.es", "Admin", UserRole.ADMIN);
            when(requestRepository.findByTokenHash(Tokens.hashear("token"))).thenReturn(Optional.of(solicitud));
            when(userRepository.findByOrganizationIdAndRoleAndStatus(any(), eq(UserRole.ADMIN), any()))
                    .thenReturn(List.of(admin));

            servicio.verificar("token");

            assertThat(solicitud.getStatus()).isEqualTo(RegistrationStatus.PENDING);
            assertThat(solicitud.getTokenHash()).isNull();
            verify(notificationService).emitirParaVarios(eq(List.of(admin)),
                    eq(Notification.REGISTRO_SOLICITADO), any());
        }

        @Test
        @DisplayName("con un token caducado da el mismo error que uno inexistente")
        void tokenCaducado() {
            RegistrationRequest solicitud = solicitudSinVerificar();
            solicitud.emitirVerificacion(Tokens.hashear("token"), Instant.now().minusSeconds(1));
            when(requestRepository.findByTokenHash(Tokens.hashear("token"))).thenReturn(Optional.of(solicitud));
            when(requestRepository.findByTokenHash(Tokens.hashear("inventado"))).thenReturn(Optional.empty());

            String mensajeCaducado = catchMessage(() -> servicio.verificar("token"));
            String mensajeInexistente = catchMessage(() -> servicio.verificar("inventado"));

            assertThat(mensajeCaducado).isEqualTo(mensajeInexistente);
            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("no se puede usar dos veces: tras verificar, el token ya no existe")
        void unSoloUso() {
            RegistrationRequest solicitud = solicitudPendiente();
            // Ya verificada: el token se borró y el estado no es UNVERIFIED
            when(requestRepository.findByTokenHash(Tokens.hashear("token"))).thenReturn(Optional.of(solicitud));

            assertThatThrownBy(() -> servicio.verificar("token")).isInstanceOf(BadRequestException.class);
        }

        private String catchMessage(Runnable accion) {
            try {
                accion.run();
            } catch (BadRequestException e) {
                return e.getMessage();
            }
            throw new AssertionError("Debía lanzar BadRequestException");
        }
    }

    @Nested
    @DisplayName("Resolver (administración)")
    class Resolver {

        @Test
        @DisplayName("aprobar crea la cuenta con el rol que elige el administrador y emite la invitación")
        void aprobar() {
            RegistrationRequest solicitud = solicitudPendiente();
            User creado = new User(centro, EMAIL, "Ana", UserRole.TEACHER);
            InvitationResponse invitacion = new InvitationResponse("http://x/invitacion/t", Instant.now(), false);
            when(authService.identidadActual()).thenReturn(administrador());
            when(requestRepository.findById(solicitud.getId())).thenReturn(Optional.of(solicitud));
            when(userService.crearCuenta(centro, "Ana", EMAIL, "teacher")).thenReturn(creado);
            when(invitationService.emitirPara(creado)).thenReturn(invitacion);

            CreateUserResponse respuesta = servicio.aprobar(solicitud.getId(), "teacher");

            assertThat(respuesta.invitation()).isSameAs(invitacion);
            assertThat(solicitud.getStatus()).isEqualTo(RegistrationStatus.APPROVED);
            assertThat(solicitud.getReviewedAt()).isNotNull();
        }

        @Test
        @DisplayName("rechazar la marca como rechazada y avisa por correo")
        void rechazar() {
            RegistrationRequest solicitud = solicitudPendiente();
            when(authService.identidadActual()).thenReturn(administrador());
            when(requestRepository.findById(solicitud.getId())).thenReturn(Optional.of(solicitud));

            servicio.rechazar(solicitud.getId());

            assertThat(solicitud.getStatus()).isEqualTo(RegistrationStatus.REJECTED);
            verify(mailService).enviarSolicitudRechazada("Ana", EMAIL);
        }

        @Test
        @DisplayName("una solicitud sin verificar no se puede aprobar: el correo aún no es de fiar")
        void sinVerificar() {
            RegistrationRequest solicitud = solicitudSinVerificar();
            solicitud.setId(UUID.randomUUID());
            when(authService.identidadActual()).thenReturn(administrador());
            when(requestRepository.findById(solicitud.getId())).thenReturn(Optional.of(solicitud));

            assertThatThrownBy(() -> servicio.aprobar(solicitud.getId(), "student"))
                    .isInstanceOf(ConflictException.class);
            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("una solicitud ya resuelta no se puede resolver otra vez")
        void yaResuelta() {
            RegistrationRequest solicitud = solicitudPendiente();
            solicitud.rechazar(null);
            when(authService.identidadActual()).thenReturn(administrador());
            when(requestRepository.findById(solicitud.getId())).thenReturn(Optional.of(solicitud));

            assertThatThrownBy(() -> servicio.rechazar(solicitud.getId())).isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("la solicitud de otro centro se trata como inexistente")
        void deOtroCentro() {
            Organization otro = new Organization("Otro", "otro.es");
            otro.setId(UUID.randomUUID());
            RegistrationRequest ajena = new RegistrationRequest(otro, "bea@otro.es", "Bea", null, IP);
            ajena.setId(UUID.randomUUID());
            when(authService.identidadActual()).thenReturn(administrador());
            when(requestRepository.findById(ajena.getId())).thenReturn(Optional.of(ajena));

            assertThatThrownBy(() -> servicio.aprobar(ajena.getId(), "student"))
                    .isInstanceOf(NotFoundException.class);
            verifyNoInteractions(userService);
        }
    }
}
