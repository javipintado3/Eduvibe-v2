package com.eduvibe.service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.eduvibe.exception.ProtectedDemoAccountException;
import com.eduvibe.model.User;

/**
 * Protege de cambios destructivos las cuentas con las que entra todo el mundo en la demo.
 *
 * Las credenciales de la demo pública están en el README y las cuentas pueden escribir, así
 * que alguien podría cambiarles la contraseña, activarles el 2FA o darlas de baja, y la demo
 * quedaría inutilizable para los siguientes visitantes hasta el próximo reinicio. Estas
 * cuentas siguen pudiendo hacer todo lo demás (publicar, calificar, crear usuarios...).
 *
 * Se configura con la lista app.demo.protected-emails (DEMO_PROTECTED_EMAILS). Vacía,
 * que es lo normal fuera de la demo, no protege a nadie.
 *
 * La regla se aplica en el servidor y no escondiendo botones en el frontend: ocultarlos no
 * impediría llamar a la API directamente.
 */
@Component
public class ProtectedDemoAccounts {

    static final String MESSAGE = "Esta es una cuenta de demostración compartida: no se puede cambiar su "
            + "contraseña, su verificación en dos pasos ni darla de baja, para que la demo siga "
            + "funcionando para todo el mundo.";

    private final Set<String> emails;

    public ProtectedDemoAccounts(@Value("${app.demo.protected-emails:}") String list) {
        this.emails = Arrays.stream(list.split(","))
                .map(String::trim)
                .filter(email -> !email.isEmpty())
                .map(User::normalizeEmail)
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isProtected(User user) {
        return user != null && emails.contains(User.normalizeEmail(user.getEmail()));
    }

    /** @throws CuentaDeDemoProtegidaException si la cuenta es una de las compartidas de la demo */
    public void requireNotProtected(User user) {
        if (isProtected(user)) {
            throw new ProtectedDemoAccountException(MESSAGE);
        }
    }
}
