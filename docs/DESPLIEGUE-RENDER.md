# Despliegue gratuito: Neon + Render + Vercel

Tres servicios gratuitos, cada uno con una pieza:

| Pieza | Servicio | Qué lleva |
|---|---|---|
| Base de datos | **Neon** | PostgreSQL |
| API | **Render** | Spring Boot (con Docker) |
| Web | **Vercel** | Angular, y reenvía `/api` y `/uploads` a la API |

La web llama siempre a `/api` en su propio dominio, y Vercel lo reenvía a Render. Así el navegador solo habla con un origen y no hay que pelearse con el CORS desde el navegador.

> Los límites de los planes gratuitos cambian. Revisa en cada web lo que incluye el plan antes de crear la cuenta.

## 1. Base de datos en Neon

1. Crea un proyecto en Neon. Elige la región **AWS Europe (Frankfurt)**: es donde `render.yaml` crea la API, y cuanto más cerca estén la API y la base de datos, más rápida va la aplicación.
2. En el panel, abre **Connection details** y apunta el host, la base de datos, el usuario y la contraseña.
3. Con eso monta la URL JDBC (la usarás en Render):

   ```
   jdbc:postgresql://<HOST>/<BASE_DE_DATOS>?sslmode=require
   ```

   Usa el host **directo** (el que no lleva `-pooler`). El *pooler* puede dar problemas con las migraciones de Flyway.

## 2. API en Render

1. En Render: **New → Blueprint**, y elige el repositorio `Eduvibe-v2`. Render lee `render.yaml` y propone el servicio `eduvibe-api`.
2. Te pedirá los valores de las variables sin rellenar:

   | Variable | Valor |
   |---|---|
   | `SPRING_DATASOURCE_URL` | la URL JDBC del paso 1 |
   | `DATABASE_USERNAME` | el usuario de Neon |
   | `DATABASE_PASSWORD` | la contraseña de Neon |
   | `CORS_ALLOWED_ORIGINS` | déjala vacía de momento (se rellena en el paso 4) |
   | `FRONTEND_URL` | déjala vacía de momento (paso 4) |

   `JWT_SECRET` se genera sola.
3. Crea el servicio. La primera construcción tarda unos minutos (compila el proyecto con Maven).
4. Cuando termine, Render te da la dirección de tu API, por ejemplo `https://eduvibe-api-x1y2.onrender.com`. Apúntala y comprueba que `<tu dirección>/api/health` responde.

> **Antes del paso 3**, sustituye las dos apariciones de `https://REEMPLAZAR-CON-TU-API.onrender.com` en `src-frontend/EduVibeFront/vercel.json` por la dirección de tu API. Está así a propósito: si apuntara a una dirección fija, otra persona que tenga un servicio con ese nombre en Render recibiría los inicios de sesión de tu web. El nombre `eduvibe-api` puede estar ya cogido por otra cuenta, y entonces Render añade un sufijo.

## 3. Web en Vercel

1. En Vercel: **Add New → Project**, y elige el mismo repositorio.
2. En **Root Directory** pon `src-frontend/EduVibeFront`. El resto (comando de construcción y carpeta de salida) ya viene en `vercel.json`.
3. Despliega. Apunta la dirección que te dé, por ejemplo `https://eduvibe-xxxx.vercel.app`.

## 4. Volver a Render con la dirección de Vercel

En el servicio `eduvibe-api` de Render, en **Environment**:

- `CORS_ALLOWED_ORIGINS` = `https://eduvibe-xxxx.vercel.app` (sin barra al final)
- `FRONTEND_URL` = la misma dirección

Guarda: Render reinicia la API.

## 5. Comprobar

1. Abre la dirección de Vercel. Si la API estaba dormida, la primera carga puede tardar de uno a tres minutos: mientras tanto sale una pantalla de espera con capturas de la aplicación.
2. En la pantalla de entrada, usa los botones de **Demostración** (todas las cuentas usan la contraseña `demo1234`).
3. Prueba a crear una clase y una tarea.

## Limitaciones del plan gratuito

- **La API se duerme** tras unos 15 minutos sin visitas, y despertarla tarda de uno a tres minutos. Un monitor (por ejemplo UptimeRobot) que visite `https://<tu-vercel>/api/health` cada pocos minutos lo evitaría, pero mantener la API siempre despierta gasta unas 744 de las 750 horas gratuitas del mes y se reparten entre todos los servicios gratuitos de la cuenta: si hay más de uno, Render los suspende. Por eso no se usa.
- **Los archivos subidos no son permanentes**: el disco se vacía al reiniciar. Los datos de ejemplo no dependen de él.
- **Memoria justa** (unos 512 MB): por eso `render.yaml` limita la memoria de Java. Si la API se reinicia sola, mira los registros de Render.
- **Es una demo pública**: cualquiera puede entrar con las cuentas demo y modificar datos. No pongas nada personal, ni conectes el correo real.

## Correo

Sin `MAIL_USERNAME` y `MAIL_PASSWORD` la API no envía correos, y al crear un usuario muestra el enlace de invitación para copiarlo a mano. Para una demo pública es lo más seguro.

## Cómo se mantiene limpia la demo

Cualquiera puede entrar con las cuentas demo y escribir, así que la demo se protege sola (todo se configura en `render.yaml`):

- **Se reinicia sola.** `DEMO_RESET_CRON` borra todos los datos y vuelve a cargar los de ejemplo cada madrugada (4:00, hora de Madrid). Como una API dormida no ejecuta tareas programadas, `DEMO_RESET_AFTER_HOURS` añade un reinicio al despertar si el último fue hace más de esas horas: quien despierta la API siempre ve la demo limpia. Ambos están apagados si no se configuran, y se niegan a ejecutarse si la base de datos no tiene los datos de ejemplo.
- **Las cuentas de acceso rápido no se pueden estropear.** `DEMO_PROTECTED_EMAILS` impide cambiar su contraseña, activar el 2FA, borrarlas o que otra cuenta las edite, desactive o elimine. El resto de acciones (publicar, calificar, crear usuarios...) funcionan con normalidad.
- **Las sesiones duran 30 minutos** (`JWT_EXPIRATION_MINUTES`), para que una cuenta olvidada no quede abierta.
