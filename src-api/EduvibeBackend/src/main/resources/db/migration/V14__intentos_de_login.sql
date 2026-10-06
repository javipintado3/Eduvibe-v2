-- Límite de intentos de inicio de sesión (spec, "Seguridad de cuenta").
-- Se guarda el email que se intentó, exista o no en la plataforma: si solo se
-- contaran los emails reales, el bloqueo delataría cuáles están dados de alta.
-- ip_address es varchar(45) para caber una dirección IPv6 completa.
-- Retención: la aplicación purga los registros de más de 90 días.
CREATE TABLE login_attempts (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email         varchar(255) NOT NULL,
  ip_address    varchar(45),
  succeeded     boolean NOT NULL,
  attempted_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_login_attempts_email ON login_attempts(email, attempted_at);
