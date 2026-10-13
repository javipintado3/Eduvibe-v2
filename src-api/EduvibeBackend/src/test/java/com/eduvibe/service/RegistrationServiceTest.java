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

    private RegistrationService service;

    private Organization organization;

    @BeforeEach
    void crearServicio() {
        AppProperties properties = new AppProperties(null, null, "http://localhost:4200", null, null, null,
                new AppProperties.Registration(24, 5, 100, false));
        service = new RegistrationService(requestRepository, organizationRepository, userRepository,
                userService, invitationService, notificationService, mailService, authService, properties);

        organization = new Organization("Centro", "centro.es");
        organization.setId(UUID.randomUUID());
    }

    private RegisterRequest request(String email) {
        return new RegisterRequest("Ana", email, null, "");
    }

    private RegistrationRequest unverifiedRequest() {
        RegistrationRequest request = new RegistrationRequest(organization, EMAIL, "Ana", null, IP);
        request.issueVerification(Tokens.hash("token"), Instant.now().plusSeconds(3600));
        return request;
    }

    private RegistrationRequest pendingRequest() {
        RegistrationRequest request = unverifiedRequest();
        request.setId(UUID.randomUUID());
        request.markAsVerified();
        return request;
    }

    private AuthenticatedUser administrator() {
        return new AuthenticatedUser(UUID.randomUUID(), "admin@centro.es", "Admin", UserRole.ADMIN, organization.getId());
    }

    @Nested
    @DisplayName("Solicitar")
    class Request {

        @Test
        @DisplayName("con un email nuevo de un dominio conocido guarda solo el hash del token y envía el enlace con el token en claro")
        void emailNuevo() {
            when(organizationRepository.findByAllowedDomain("centro.es")).thenReturn(Optional.of(organization));
            when(requestRepository.findFirstByEmailAndStatusIn(eq(EMAIL), anyCollection())).thenReturn(Optional.empty());

            service.request(request("  Ana@Centro.ES "), IP);

            ArgumentCaptor<RegistrationRequest> saved = ArgumentCaptor.forClass(RegistrationRequest.class);
            verify(requestRepository).save(saved.capture());
            ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
            verify(mailService).sendVerificationOfRegistration(eq("Ana"), eq(EMAIL), link.capture());

            String token = link.getValue().substring(link.getValue().lastIndexOf('/') + 1);
            assertThat(link.getValue()).startsWith("http://localhost:4200/verificar-registro/");
            assertThat(saved.getValue().getTokenHash()).isEqualTo(Tokens.hash(token));
            assertThat(saved.getValue().getTokenHash()).isNotEqualTo(token);
            assertThat(saved.getValue().getStatus()).isEqualTo(RegistrationStatus.UNVERIFIED);
            assertThat(saved.getValue().getIpAddress()).isEqualTo(IP);
        }

        @Test
        @DisplayName("guarda el mensaje sin espacios sobrantes, y uno en blanco se guarda como ausente")
        void mensaje() {
            when(organizationRepository.findByAllowedDomain("centro.es")).thenReturn(Optional.of(organization));
            when(requestRepository.findFirstByEmailAndStatusIn(anyString(), anyCollection()))
                    .thenReturn(Optional.empty());

            service.request(new RegisterRequest("Ana", EMAIL, "  Soy de 2.º B  ", ""), IP);
            service.request(new RegisterRequest("Bea", "bea@centro.es", "   ", ""), IP);

            ArgumentCaptor<RegistrationRequest> saved = ArgumentCaptor.forClass(RegistrationRequest.class);
            verify(requestRepository, org.mockito.Mockito.times(2)).save(saved.capture());
            assertThat(saved.getAllValues().get(0).getMessage()).isEqualTo("Soy de 2.º B");
            assertThat(saved.getAllValues().get(1).getMessage()).isNull();
        }

        @Test
        @DisplayName("si el dominio no corresponde a ningún centro no guarda ni envía nada, y no da error")
        void dominioDesconocido() {
            when(organizationRepository.findByAllowedDomain("otro.com")).thenReturn(Optional.empty());

            service.request(request("ana@otro.com"), IP);

            verify(requestRepository, never()).save(any());
            verifyNoInteractions(mailService);
        }

        @Test
        @DisplayName("si ya existe una cuenta con ese email no guarda ni envía nada, y no da error")
        void cuentaExistente() {
            when(organizationRepository.findByAllowedDomain("centro.es")).thenReturn(Optional.of(organization));
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            service.request(request(EMAIL), IP);

            verify(requestRepository, never()).save(any());
            verifyNoInteractions(mailService);
        }

        @Test
        @DisplayName("si se rechazó a ese email hace poco, lo ignora sin avisar")
        void rechazadaHacePoco() {
            when(organizationRepository.findByAllowedDomain("centro.es")).thenReturn(Optional.of(organization));
            when(requestRepository.existsByEmailAndStatusAndReviewedAtAfter(
                    eq(EMAIL), eq(RegistrationStatus.REJECTED), any())).thenReturn(true);

            service.request(request(EMAIL), IP);

            verify(requestRepository, never()).save(any());
            verifyNoInteractions(mailService);
        }

        @Test
        @DisplayName("si el campo cebo viene relleno lo descarta sin tocar nada")
        void bot() {
            service.request(new RegisterRequest("Bot", EMAIL, null, "http://spam.example"), IP);

            verifyNoInteractions(requestRepository, organizationRepository, userRepository, mailService);
        }

        @Test
        @DisplayName("si la IP supera el límite por hora lanza 429")
        void limitePorIp() {
            when(requestRepository.countByIpAddressAndCreatedAtAfter(eq(IP), any())).thenReturn(5L);

            assertThatThrownBy(() -> service.request(request(EMAIL), IP))
                    .isInstanceOf(TooManyRequestsException.class);
            verify(requestRepository, never()).save(any());
        }

        @Test
        @DisplayName("si la plataforma entera supera el tope por hora lanza 429, aunque la IP esté limpia")
        void limiteGlobal() {
            when(requestRepository.countByCreatedAtAfter(any())).thenReturn(100L);

            assertThatThrownBy(() -> service.request(request(EMAIL), IP))
                    .isInstanceOf(TooManyRequestsException.class);
        }

        @Test
        @DisplayName("si ya hay una solicitud sin verificar y se pidió el enlace hace poco, no envía otro")
        void reenvioDemasiadoPronto() {
            RegistrationRequest open = unverifiedRequest();
            open.setUpdatedAt(Instant.now().minusSeconds(30));
            when(organizationRepository.findByAllowedDomain("centro.es")).thenReturn(Optional.of(organization));
            when(requestRepository.findFirstByEmailAndStatusIn(eq(EMAIL), anyCollection()))
                    .thenReturn(Optional.of(open));

            service.request(request(EMAIL), IP);

            verifyNoInteractions(mailService);
        }

        @Test
        @DisplayName("si ya hay una solicitud sin verificar y pasó la espera, emite un token nuevo que sustituye al anterior")
        void reenvioPasadaLaEspera() {
            RegistrationRequest open = unverifiedRequest();
            String previousHash = open.getTokenHash();
            open.setUpdatedAt(Instant.now().minusSeconds(3600));
            when(organizationRepository.findByAllowedDomain("centro.es")).thenReturn(Optional.of(organization));
            when(requestRepository.findFirstByEmailAndStatusIn(eq(EMAIL), anyCollection()))
                    .thenReturn(Optional.of(open));

            service.request(request(EMAIL), IP);

            verify(mailService).sendVerificationOfRegistration(anyString(), eq(EMAIL), anyString());
            assertThat(open.getTokenHash()).isNotEqualTo(previousHash);
        }

        @Test
        @DisplayName("si la solicitud ya está pendiente de aprobar, no envía nada")
        void yaPendiente() {
            RegistrationRequest open = pendingRequest();
            when(organizationRepository.findByAllowedDomain("centro.es")).thenReturn(Optional.of(organization));
            when(requestRepository.findFirstByEmailAndStatusIn(eq(EMAIL), anyCollection()))
                    .thenReturn(Optional.of(open));

            service.request(request(EMAIL), IP);

            verifyNoInteractions(mailService);
        }
    }

    @Nested
    @DisplayName("Verificar")
    class Verify {

        @Test
        @DisplayName("con un token válido pasa a pendiente, invalida el token y avisa a los administradores")
        void tokenValido() {
            RegistrationRequest request = unverifiedRequest();
            User admin = new User(organization, "admin@centro.es", "Admin", UserRole.ADMIN);
            when(requestRepository.findByTokenHash(Tokens.hash("token"))).thenReturn(Optional.of(request));
            when(userRepository.findByOrganizationIdAndRoleAndStatus(any(), eq(UserRole.ADMIN), any()))
                    .thenReturn(List.of(admin));

            service.verify("token");

            assertThat(request.getStatus()).isEqualTo(RegistrationStatus.PENDING);
            assertThat(request.getTokenHash()).isNull();
            verify(notificationService).issueForSeveral(eq(List.of(admin)),
                    eq(Notification.REGISTRATION_REQUESTED), any());
        }

        @Test
        @DisplayName("con un token caducado da el mismo error que uno inexistente")
        void tokenCaducado() {
            RegistrationRequest request = unverifiedRequest();
            request.issueVerification(Tokens.hash("token"), Instant.now().minusSeconds(1));
            when(requestRepository.findByTokenHash(Tokens.hash("token"))).thenReturn(Optional.of(request));
            when(requestRepository.findByTokenHash(Tokens.hash("inventado"))).thenReturn(Optional.empty());

            String expiredMessage = catchMessage(() -> service.verify("token"));
            String nonexistentMessage = catchMessage(() -> service.verify("inventado"));

            assertThat(expiredMessage).isEqualTo(nonexistentMessage);
            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("no se puede usar dos veces: tras verificar, el token ya no existe")
        void unSoloUso() {
            RegistrationRequest request = pendingRequest();
            // Ya verificada: el token se borró y el estado no es UNVERIFIED
            when(requestRepository.findByTokenHash(Tokens.hash("token"))).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.verify("token")).isInstanceOf(BadRequestException.class);
        }

        private String catchMessage(Runnable action) {
            try {
                action.run();
            } catch (BadRequestException e) {
                return e.getMessage();
            }
            throw new AssertionError("Debía lanzar BadRequestException");
        }
    }

    @Nested
    @DisplayName("Resolver (administración)")
    class Resolve {

        @Test
        @DisplayName("aprobar crea la cuenta con el rol que elige el administrador y emite la invitación")
        void aprobar() {
            RegistrationRequest request = pendingRequest();
            User creado = new User(organization, EMAIL, "Ana", UserRole.TEACHER);
            InvitationResponse invitation = new InvitationResponse("http://x/invitacion/t", Instant.now(), false);
            when(authService.currentIdentity()).thenReturn(administrator());
            when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
            when(userService.createAccount(organization, "Ana", EMAIL, "teacher")).thenReturn(creado);
            when(invitationService.issueFor(creado)).thenReturn(invitation);

            CreateUserResponse answer = service.approve(request.getId(), "teacher");

            assertThat(answer.invitation()).isSameAs(invitation);
            assertThat(request.getStatus()).isEqualTo(RegistrationStatus.APPROVED);
            assertThat(request.getReviewedAt()).isNotNull();
        }

        @Test
        @DisplayName("rechazar la marca como rechazada y avisa por correo")
        void rechazar() {
            RegistrationRequest request = pendingRequest();
            when(authService.currentIdentity()).thenReturn(administrator());
            when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));

            service.reject(request.getId());

            assertThat(request.getStatus()).isEqualTo(RegistrationStatus.REJECTED);
            verify(mailService).sendRejectedRequest("Ana", EMAIL);
        }

        @Test
        @DisplayName("una solicitud sin verificar no se puede aprobar: el correo aún no es de fiar")
        void sinVerificar() {
            RegistrationRequest request = unverifiedRequest();
            request.setId(UUID.randomUUID());
            when(authService.currentIdentity()).thenReturn(administrator());
            when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.approve(request.getId(), "student"))
                    .isInstanceOf(ConflictException.class);
            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("una solicitud ya resuelta no se puede resolver otra vez")
        void yaResuelta() {
            RegistrationRequest request = pendingRequest();
            request.reject(null);
            when(authService.currentIdentity()).thenReturn(administrator());
            when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.reject(request.getId())).isInstanceOf(ConflictException.class);
        }

        @Test
        @DisplayName("la solicitud de otro centro se trata como inexistente")
        void deOtroCentro() {
            Organization otherOrganization = new Organization("Otro", "otro.es");
            otherOrganization.setId(UUID.randomUUID());
            RegistrationRequest foreignRequest = new RegistrationRequest(otherOrganization, "bea@otro.es", "Bea", null, IP);
            foreignRequest.setId(UUID.randomUUID());
            when(authService.currentIdentity()).thenReturn(administrator());
            when(requestRepository.findById(foreignRequest.getId())).thenReturn(Optional.of(foreignRequest));

            assertThatThrownBy(() -> service.approve(foreignRequest.getId(), "student"))
                    .isInstanceOf(NotFoundException.class);
            verifyNoInteractions(userService);
        }
    }
}
