-- Mensaje opcional de quien pide la cuenta para la administración (quién es,
-- en qué curso está...). Es texto libre y lo escribe alguien que aún no ha
-- demostrado nada, así que solo se muestra, nunca se interpreta, y se limita
-- su longitud.
ALTER TABLE registration_requests ADD COLUMN message varchar(500);
