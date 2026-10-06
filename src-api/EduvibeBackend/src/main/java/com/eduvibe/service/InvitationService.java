package com.eduvibe.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.config.AppProperties;
import com.eduvibe.dto.auth.InvitationInfoResponse;
import com.eduvibe.dto.user.InvitationResponse;
import com.eduvibe.exception.BadRequestException;
import com.eduvibe.model.Invitation;
import com.eduvibe.model.User;
import com.eduvibe.repository.InvitationRepository;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.util.Tokens;

import lombok.RequiredArgsConstructor;

/**
 * Altas por invitación.
 *
 * El alta de una cuenta nunca incluye una contraseña: el administrador crea al
 * usuario y el sistema emite un enlace de un solo uso con el que la persona
 * establece la suya. Nunca viaja una contraseña por correo.
 *
 * Del token solo se guarda su hash. Quien consiga leer la base de datos no
 * puede usar las invitaciones pendientes, igual que no puede usar las
 * contraseñas.
 */
@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final AppProperties propiedades;

    /**
     * Emite una invitación para el usuario y, si se puede, la envía por correo.
     *
     * Las invitaciones anteriores que siguieran vivas quedan invalidadas, para
     * que reenviar no deje dos enlaces válidos a la vez.
     */
    @Transactional
    public InvitationResponse emitirPara(User usuario) {
        invitationRepository.invalidarPendientesDe(usuario.getId());

        String token = Tokens.generar();
        Instant caducidad = Instant.now()
                .plus(Duration.ofHours(propiedades.invitation().expirationHours()));

        invitationRepository.save(new Invitation(usuario, Tokens.hashear(token), caducidad));

        String enlace = construirEnlace(token);
        boolean enviado = mailService.enviarInvitacion(usuario, enlace);

        return new InvitationResponse(enlace, caducidad, enviado);
    }

    /**
     * Datos que se muestran al abrir el enlace, para que la persona vea a qué
     * cuenta corresponde antes de escribir nada.
     */
    @Transactional(readOnly = true)
    public InvitationInfoResponse consultar(String token) {
        Invitation invitacion = buscarUtilizable(token);
        User usuario = invitacion.getUser();

        return new InvitationInfoResponse(
                usuario.getName(),
                usuario.getEmail(),
                usuario.getOrganization().getName(),
                invitacion.getExpiresAt());
    }

    /**
     * Consume la invitación: establece la contraseña y activa la cuenta.
     */
    @Transactional
    public User aceptar(String token, String password) {
        Invitation invitacion = buscarUtilizable(token);

        User usuario = invitacion.getUser();
        usuario.activarCon(passwordEncoder.encode(password));
        userRepository.save(usuario);

        invitacion.marcarComoUsada();
        invitationRepository.save(invitacion);

        return usuario;
    }

    /**
     * Busca la invitación y comprueba que sirva.
     *
     * Los tres casos (no existe, ya usada, caducada) devuelven el mismo error
     * de forma deliberada: distinguirlos permitiría averiguar qué tokens han
     * existido alguna vez.
     */
    private Invitation buscarUtilizable(String token) {
        return invitationRepository.findByTokenHash(Tokens.hashear(token))
                .filter(Invitation::esUtilizable)
                .orElseThrow(() -> new BadRequestException(
                        "La invitación no es válida o ha caducado. Pide que te la vuelvan a enviar."));
    }

    private String construirEnlace(String token) {
        String base = propiedades.frontendUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/invitacion/" + token;
    }
}
