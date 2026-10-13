# EduVibe API

The REST API of EduVibe. It is stateless (JWT), talks to PostgreSQL, and keeps the database schema under version control with Flyway.

**Java 17 · Spring Boot 3.2 · PostgreSQL 16 · Maven** (the code is in [`EduvibeBackend/`](EduvibeBackend))

For the full overview of the project, the features and the configuration table, see the [main README](../README.md). This page only covers what you need to work on the API.

## Run it

```bash
# from the repository root: start the database
docker compose -f Docker-compose.yml up -d db

# then start the API (port 9090)
cd src-api/EduvibeBackend
./mvnw spring-boot:run
```

- The API listens on http://localhost:9090. A quick check: `GET /api/health`.
- On the first start, Flyway creates the schema and loads the demo data (three accounts, password `demo1234`).
- If you run the frontend with `npm start` (port 4200), add `http://localhost:4200` to `CORS_ALLOWED_ORIGINS`.

## Tests

```bash
cd src-api/EduvibeBackend
./mvnw test
```

They are unit tests (JUnit 5 and Mockito). They do not need a database.

## Project layout

Everything is under `EduvibeBackend/src/main/java/com/eduvibe/`:

| Folder | What it does |
|---|---|
| `controller/` | Thin REST layer. It validates the input and calls a service. No business logic here. |
| `service/` | Business rules and permissions. |
| `repository/` | Spring Data JPA. Entities are never returned directly through the API. |
| `dto/` | Input and output contracts, separate from the entities. |
| `model/` | JPA entities and enums. |
| `security/` | JWT filter and service, and the 401/403 handlers. |
| `config/` | Security, CORS, typed configuration properties and the demo reset job. |
| `exception/` | Errors the API knows how to turn into a clean response. |
| `util/` | TOTP (two-step login), single-use tokens, CSV parsing, client IP. |

In `src/main/resources/`:

| Folder | What it does |
|---|---|
| `db/migration/` | The database schema, one Flyway migration per change (`V1` … `V21`). |
| `db/demo/` | The demo data. It is a repeatable migration that only loads when you ask for it. |

## Database migrations

- The schema only changes through Flyway. Hibernate runs with `ddl-auto=validate`: it checks the entities but never alters the database.
- To change the schema, **add a new file** called `V<next number>__<short description>.sql`. Never edit a migration that has already been applied.
- Keep changes backward compatible when you can (for example, add an optional column). That way the previous version of the app still works if you need to roll back.
- Demo data lives in `db/demo`. It is selected with `FLYWAY_LOCATIONS`. A production database should only use `classpath:db/migration`.

## The main areas of the API

| Area | What it covers |
|---|---|
| Auth | Login, invitations, password recovery, password change, two-step login |
| Users and organization | Admin panel for people, allowed email domain, CSV import and export |
| Registration requests | Public requests that an admin approves or rejects |
| Classes and groups | Classes, units, people in a class, groups |
| Assignments and submissions | Tasks, hand-ins, rubric grading |
| Exams | Question bank, timed attempts, automatic grading |
| Forum, announcements, calendar, notifications | Communication inside a class |
| Profile | Personal data export and erasure (GDPR) |
| Uploads | Safe file uploads (generated names, extension allow-list, size limit) |

Who can do what is decided in one place for classes (`ClassAccessService`), and the role rules for each path are in `SecurityConfig`. Everything under `/api/users/**` is admin only.

## Public demo mode

A public demo needs a few extra protections. They are all configured with environment variables and they are **off by default**:

| Variable | What it does |
|---|---|
| `DEMO_RESET_CRON` | Wipes the database and reloads the demo data on a schedule. |
| `DEMO_RESET_AFTER_HOURS` | Also resets on startup if the last reset was too long ago (a sleeping API does not run the schedule). |
| `DEMO_PROTECTED_EMAILS` | Shared demo accounts that cannot change their password or be deleted. |
| `JWT_EXPIRATION_MINUTES` | Short sessions. |

See the [deployment guide](../docs/DESPLIEGUE-RENDER.md) for how they are used on the free hosting.

## Docker

`EduvibeBackend/Dockerfile` builds the jar in one stage and runs it in a small JRE image, on port 9090.
