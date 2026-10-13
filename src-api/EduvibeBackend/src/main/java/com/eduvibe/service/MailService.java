package com.eduvibe.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.eduvibe.model.User;

import lombok.RequiredArgsConstructor;

/**
 * Envío de correo.
 *
 * Un fallo al enviar no tumba la operación que lo provocó: dar de alta a un
 * usuario tiene que funcionar aunque el servidor de correo no esté configurado,
 * que es lo normal en un entorno de demostración. Por eso los métodos devuelven
 * si se ha podido enviar en lugar de propagar la excepción, y el enlace de
 * invitación viaja también en la respuesta de la API.
 */
@Service
@RequiredArgsConstructor
public class MailService {

    private static final Logger LOG = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:}")
    private String sender;

    /**
     * Enlace para elegir una contraseña nueva.
     *
     * Va en segundo plano (@Async) para que la petición no tarde más cuando el
     * email existe que cuando no: esa diferencia de tiempo delataría qué
     * direcciones están dadas de alta, aunque la respuesta sea idéntica.
     */
    @Async
    public void sendReset(User recipient, String link) {
        if (sender == null || sender.isBlank()) {
            LOG.info("Correo no configurado. Enlace para restablecer la contraseña de {}: {}",
                    recipient.getEmail(), link);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(sender);
            message.setTo(recipient.getEmail());
            message.setSubject("Restablece tu contraseña de Eduvibe");
            message.setText("""
                    Hola %s:

                    Has pedido restablecer tu contraseña en Eduvibe. Para elegir una
                    nueva, entra en este enlace:

                    %s

                    El enlace sirve una sola vez y caduca pronto. Si no lo has pedido
                    tú, ignora este correo: tu contraseña no cambiará.
                    """.formatted(recipient.getName(), link));

            mailSender.send(message);

        } catch (Exception e) {
            LOG.warn("No se ha podido enviar el restablecimiento a {}: {}",
                    recipient.getEmail(), e.getMessage());
        }
    }

    /**
     * Enlace para confirmar que el correo de una solicitud de registro es de
     * quien la hizo. En segundo plano por la misma razón que el restablecimiento:
     * que la respuesta tarde igual haya o no que enviar nada.
     */
    @Async
    public void sendVerificationOfRegistration(String name, String email, String link) {
        if (sender == null || sender.isBlank()) {
            LOG.info("Correo no configurado. Enlace para confirmar la solicitud de registro de {}: {}",
                    email, link);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(sender);
            message.setTo(email);
            message.setSubject("Confirma tu correo en Eduvibe");
            message.setText("""
                    Hola %s:

                    Has pedido una cuenta en Eduvibe. Para confirmar que este correo es
                    tuyo, entra en este enlace:

                    %s

                    Después, la administración de tu centro revisará la solicitud y, si la
                    acepta, recibirás otro correo para elegir tu contraseña.

                    Si no lo has pedido tú, ignora este correo: no se creará ninguna cuenta.
                    """.formatted(name, link));

            mailSender.send(message);

        } catch (Exception e) {
            LOG.warn("No se ha podido enviar la confirmación de registro a {}: {}", email, e.getMessage());
        }
    }

    /** Aviso de que la administración ha rechazado la solicitud. */
    @Async
    public void sendRejectedRequest(String name, String email) {
        if (sender == null || sender.isBlank()) {
            LOG.info("Correo no configurado. Solicitud de registro de {} rechazada", email);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(sender);
            message.setTo(email);
            message.setSubject("Tu solicitud en Eduvibe");
            message.setText("""
                    Hola %s:

                    La administración de tu centro no ha aceptado tu solicitud de cuenta en
                    Eduvibe. Si crees que es un error, ponte en contacto con tu centro.
                    """.formatted(name));

            mailSender.send(message);

        } catch (Exception e) {
            LOG.warn("No se ha podido avisar del rechazo a {}: {}", email, e.getMessage());
        }
    }

    /**
     * @return true si el correo ha salido; false si no hay remitente
     *         configurado o el envío ha fallado.
     */
    public boolean sendInvitation(User recipient, String link) {
        if (sender == null || sender.isBlank()) {
            LOG.info("Correo no configurado. Enlace de invitación para {}: {}",
                    recipient.getEmail(), link);
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(sender);
            message.setTo(recipient.getEmail());
            message.setSubject("Tu acceso a Eduvibe");
            message.setText("""
                    Hola %s:

                    Se ha creado una cuenta para ti en Eduvibe. Para activarla y
                    establecer tu contraseña, entra en este enlace:

                    %s

                    El enlace sirve una sola vez. Si caduca, pide a tu centro que
                    te lo vuelva a enviar.
                    """.formatted(recipient.getName(), link));

            mailSender.send(message);
            return true;

        } catch (Exception e) {
            // No se propaga: el alta ya se ha hecho y el enlace se devuelve en
            // la respuesta, así que el administrador puede entregarlo a mano.
            LOG.warn("No se ha podido enviar la invitación a {}: {}",
                    recipient.getEmail(), e.getMessage());
            return false;
        }
    }
}
