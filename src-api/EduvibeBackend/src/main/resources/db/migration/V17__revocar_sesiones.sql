-- Cerrar sesión de verdad: los tokens JWT no se pueden "borrar", así que se
-- invalidan por dos vías.
--
-- 1. token_version: cada token lleva la versión que tenía el usuario al emitirse.
--    Subirla invalida de golpe todos los tokens anteriores de esa persona (al
--    cambiar o restablecer la contraseña).
-- 2. revoked_tokens: lista de tokens concretos revocados (cerrar sesión en un
--    dispositivo, sin echar al usuario de los demás). Cada fila solo hace falta
--    hasta que el token caducaría solo, y la aplicación la purga después.
ALTER TABLE users
  ADD COLUMN token_version integer NOT NULL DEFAULT 0;

CREATE TABLE revoked_tokens (
  jti         uuid PRIMARY KEY,
  user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  expires_at  timestamptz NOT NULL,
  revoked_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_revoked_tokens_expires ON revoked_tokens(expires_at);
