-- Verificación en dos pasos (2FA con TOTP): spec, "Seguridad de cuenta".
-- totp_secret se rellena al empezar la configuración, pero solo cuenta cuando
-- totp_enabled pasa a true, tras demostrar con un código que la app del móvil
-- lo ha leído bien.
ALTER TABLE users
  ADD COLUMN totp_secret  varchar(64),
  ADD COLUMN totp_enabled boolean NOT NULL DEFAULT false;

-- Códigos de recuperación de un solo uso, por si se pierde el móvil. Se guarda
-- el hash, nunca el código: igual que las contraseñas y los tokens.
CREATE TABLE totp_recovery_codes (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  code_hash   text NOT NULL,
  used_at     timestamptz,
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_totp_recovery_user ON totp_recovery_codes(user_id);
