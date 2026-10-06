-- RGPD: registro de las solicitudes de exportación y de borrado de datos (spec,
-- sección "RGPD básico"). Sirve de constancia de que se atendieron y cuándo.
-- Hoy se atienden al momento, por eso se guardan ya como completadas; el estado
-- 'pending' y 'rejected' quedan previstos por si algún día hay revisión manual.
--
-- No lleva ON DELETE CASCADE hacia el borrado de datos de la persona: al borrar
-- una cuenta el usuario no se elimina (se conserva su expediente), así que el
-- registro de la solicitud tampoco.
CREATE TABLE gdpr_requests (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type          varchar(20) NOT NULL CHECK (type IN ('export', 'erase')),
  status        varchar(20) NOT NULL DEFAULT 'pending'
                  CHECK (status IN ('pending', 'completed', 'rejected')),
  requested_at  timestamptz NOT NULL DEFAULT now(),
  resolved_at   timestamptz
);
CREATE INDEX idx_gdpr_requests_user ON gdpr_requests(user_id);
