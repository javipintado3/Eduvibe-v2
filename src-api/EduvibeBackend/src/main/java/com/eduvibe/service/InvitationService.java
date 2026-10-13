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
    private final AppProperties properties;

    /**
     * Emite una invitación para el usuario y, si se puede, la envía por correo.
     *
     * Las invitaciones anteriores que siguieran vivas quedan invalidadas, para
     * que reenviar no deje dos enlaces válidos a la vez.
     */
    @Transactional
    public InvitationResponse issueFor(User user) {
        invitationRepository.invalidatePendingFor(user.getId());

        String token = Tokens.generate();
        Instant expiry = Instant.now()
                .plus(Duration.ofHours(properties.invitation().expirationHours()));

        invitationRepository.save(new Invitation(user, Tokens.hash(token), expiry));

        String link = buildLink(token);
        boolean sent = mailService.sendInvitation(user, link);

        return new InvitationResponse(link, expiry, sent);
    }

    /**
     * Datos que se muestran al abrir el enlace, para que la persona vea a qué
     * cuenta corresponde antes de escribir nada.
     */
    @Transactional(readOnly = true)
    public InvitationInfoResponse lookup(String token) {
        Invitation invitation = findUsable(token);
        User user = invitation.getUser();

        return new InvitationInfoResponse(
                user.getName(),
                user.getEmail(),
                user.getOrganization().getName(),
                invitation.getExpiresAt());
    }

    /**
     * Consume la invitación: establece la contraseña y activa la cuenta.
     */
    @Transactional
    public User accept(String token, String password) {
        Invitation invitation = findUsable(token);

        User user = invitation.getUser();
        user.enableWith(passwordEncoder.encode(password));
        userRepository.save(user);

        invitation.markAsUsed();
        invitationRepository.save(invitation);

        return user;
    }

    /**
     * Busca la invitación y comprueba que sirva.
     *
     * Los tres casos (no existe, ya usada, caducada) devuelven el mismo error
     * de forma deliberada: distinguirlos permitiría averiguar qué tokens han
     * existido alguna vez.
     */
    private Invitation findUsable(String token) {
        return invitationRepository.findByTokenHash(Tokens.hash(token))
                .filter(Invitation::isUsable)
                .orElseThrow(() -> new BadRequestException(
                        "La invitación no es válida o ha caducado. Pide que te la vuelvan a enviar."));
    }

    private String buildLink(String token) {
        String base = properties.frontendUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/invitacion/" + token;
    }
}
