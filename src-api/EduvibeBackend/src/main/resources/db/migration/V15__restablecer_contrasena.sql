-- "Olvidé mi contraseña": enlace de un solo uso para elegir una contraseña nueva.
-- Tabla aparte de invitations a propósito: una invitación activa la cuenta, y
-- reutilizarla permitiría reactivar con un enlace de recuperación una cuenta
-- que la administración ha desactivado.
-- Igual que en las invitaciones, se guarda el hash del token, nunca el token.
CREATE TABLE password_resets (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash    text NOT NULL UNIQUE,
  expires_at    timestamptz NOT NULL,
  used_at       timestamptz,
  created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_resets_user ON password_resets(user_id, created_at);
