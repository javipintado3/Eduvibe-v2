package com.eduvibe.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.model.Organization;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.UserRole;
import com.eduvibe.repository.OrganizationRepository;
import com.eduvibe.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Crea el primer administrador cuando la base de datos está vacía.
 *
 * No hay registro público, y sin los datos de demostración no existe ninguna
 * cuenta: sin este arranque, una instalación limpia sería inaccesible. Solo
 * actúa si no hay ningún usuario, así que no toca nada en una base ya en uso
 * ni en una con datos demo, y es seguro dejarlo configurado entre reinicios.
 *
 * Las credenciales llegan por entorno y nunca se escriben en el código ni en
 * las migraciones, para no dejar una cuenta conocida en el repositorio.
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrap.class);

    /** Mínimo para una contraseña de administración: es la cuenta con más poder. */
    static final int LONGITUD_MINIMA_PASSWORD = 12;

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin-email:}")
    private String email;

    @Value("${app.bootstrap.admin-password:}")
    private String password;

    @Value("${app.bootstrap.admin-name:Administrador}")
    private String nombre;

    @Value("${app.bootstrap.organization-name:EduVibe}")
    private String organizacion;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        if (email.isBlank() || password.isBlank()) {
            LOG.warn("La base de datos no tiene usuarios y no hay ADMIN_EMAIL / ADMIN_PASSWORD: "
                    + "nadie podrá entrar. Configúralos para crear el primer administrador.");
            return;
        }
        if (password.length() < LONGITUD_MINIMA_PASSWORD) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD debe tener al menos %d caracteres".formatted(LONGITUD_MINIMA_PASSWORD));
        }

        Organization centro = organizationRepository.save(new Organization(organizacion, null));
        User admin = new User(centro, email, nombre, UserRole.ADMIN);
        admin.activarCon(passwordEncoder.encode(password));
        userRepository.save(admin);

        LOG.info("Primer administrador creado ({}). Cambia la contraseña desde el perfil y "
                + "retira ADMIN_PASSWORD de la configuración.", admin.getEmail());
    }
}
