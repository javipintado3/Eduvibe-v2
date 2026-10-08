-- Solicitudes de registro: alguien pide una cuenta por su cuenta y un
-- administrador la acepta o la rechaza.
--
-- Una solicitud NO es un usuario. Hasta que se aprueba no existe fila en
-- users, así que una solicitud falsa o sin verificar no puede iniciar sesión
-- ni aparecer en el listado de usuarios, ni ocupa un email.
--
-- Ciclo de vida:
--   unverified -> la persona aún no ha confirmado que el correo es suyo
--   pending    -> correo confirmado; espera la decisión de un administrador
--   approved   -> se creó la cuenta (en users) y se envió su invitación
--   rejected   -> denegada
--
-- No se guarda contraseña: la elige la persona al aceptar la invitación, como
-- en cualquier otra alta. El rol tampoco lo elige quien solicita: lo fija el
-- administrador al aprobar, y por eso no hay columna de rol.
CREATE TABLE registration_requests (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id            uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  email             varchar(255) NOT NULL,    -- normalizado a minúsculas por la aplicación
  name              text NOT NULL,
  status            varchar(20) NOT NULL DEFAULT 'unverified'
                      CHECK (status IN ('unverified', 'pending', 'approved', 'rejected')),
  -- Hash del token de verificación del correo (nunca el token), como en invitaciones
  token_hash        text UNIQUE,
  token_expires_at  timestamptz,
  -- Para limitar peticiones por IP. varchar(45) cabe una IPv6 completa
  ip_address        varchar(45),
  verified_at       timestamptz,
  reviewed_at       timestamptz,
  reviewed_by       uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_registration_requests_updated BEFORE UPDATE ON registration_requests
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Como mucho una solicitud abierta por email: reintentar no apila filas
CREATE UNIQUE INDEX idx_registration_requests_abierta
  ON registration_requests (email) WHERE status IN ('unverified', 'pending');
CREATE INDEX idx_registration_requests_org ON registration_requests (org_id, status, created_at);
CREATE INDEX idx_registration_requests_ip ON registration_requests (ip_address, created_at);
