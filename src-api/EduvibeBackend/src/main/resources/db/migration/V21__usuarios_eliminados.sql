-- Eliminación de usuarios por parte de la administración.
--
-- No se borra la fila: los mensajes de foro, anuncios, exámenes y notas que la
-- persona dejó apuntan a ella, y borrarla dejaría ese contenido huérfano. En su
-- lugar se anonimiza (nombre "Usuario eliminado", email imposible, sin
-- contraseña) y se marca con esta fecha para sacarla de los listados.
--
-- Columna nueva y opcional: las versiones anteriores de la aplicación la
-- ignoran, así que se puede volver atrás sin tocar el esquema.
ALTER TABLE users ADD COLUMN deleted_at timestamptz;
