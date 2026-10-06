package com.eduvibe.dto.auth;

/**
 * Lo necesario para añadir la cuenta a una app de autenticación: el secreto
 * para teclearlo a mano y el enlace con el que el frontend dibuja el QR.
 */
public record TwoFactorSetupResponse(String secret, String otpauthUri) {
}
