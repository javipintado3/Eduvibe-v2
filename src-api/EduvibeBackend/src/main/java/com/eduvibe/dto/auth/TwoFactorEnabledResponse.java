package com.eduvibe.dto.auth;

import java.util.List;

/**
 * Respuesta al activar el 2FA: los códigos de recuperación, en claro. Es la
 * única vez que se pueden ver; después solo queda su hash.
 */
public record TwoFactorEnabledResponse(List<String> recoveryCodes) {
}
