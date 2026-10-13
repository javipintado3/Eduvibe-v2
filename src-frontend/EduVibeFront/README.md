# EduVibe frontend

The web app of EduVibe, built with **Angular 17**. It uses standalone components and signals. There is no NgRx and no UI library: the design system is custom.

For the full overview of the project, see the [main README](../../README.md). This page only covers what you need to work on the frontend.

## Run it

```bash
cd src-frontend/EduVibeFront
npm install
npm start
```

The app opens at http://localhost:4200. It needs the API running on http://localhost:9090 (see the [API README](../../src-api/README.md)), and `http://localhost:4200` must be in the API's `CORS_ALLOWED_ORIGINS`.

The API address for development is in `src/environments/environment.ts`.

## Commands

| Command | What it does |
|---|---|
| `npm start` | Dev server with reload |
| `npm run build` | Production build into `dist/` |
| `npm run build:vercel` | Production build plus the API address replacement (see below) |
| `npm test` | Unit tests with Karma |

The unit tests cover the list helpers (filtering, sorting, pagination and the weighted average).

## Project layout

Everything is under `src/app/`:

| Folder | What it contains |
|---|---|
| `core/` | Route guards, the HTTP interceptor, models, one service per domain (`TareasService`, `ForoService`, …) and small utilities |
| `layout/` | The shell with the navbar and footer |
| `paginas/` | One folder per screen. Each one is lazy loaded with `loadComponent` |
| `shared/` | Reusable pieces: data table, paginator, dialogs, status pills, avatar, logo… |

Images for the loading screen are in `src/assets/carga/`.

A few rules the code follows:

- Components never call `HttpClient` directly. They use the service of their domain.
- The session guard sits once on the layout's parent route, so every new screen is protected by default.
- A custom confirmation dialog is used instead of `window.confirm`.

## The API address in production

`src/environments/environment.prod.ts` does not contain the API address. It has a placeholder that is replaced when the app is built, so you do not need to rebuild for each environment.

- **Docker:** the `Dockerfile` replaces it with `sed`. By default it uses `/api`, and nginx forwards that to the API. Use `--build-arg API_URL=https://…` for another API.
- **Vercel:** `npm run build:vercel` runs `scripts/inyectar-api-url.mjs`. By default it uses `/api`, and `vercel.json` forwards that to the API on Render.

`nginx.conf` also sends unknown paths back to `index.html`, so direct links (invitations, password reset) do not give a 404.

## The "waking up the server" screen

On the free hosting, the API goes to sleep when nobody uses it, and it can take one to three minutes to wake up. When the app opens, `ServidorService` calls `/api/health`. If the API takes more than one second to answer, `DespertarServidorComponent` covers the app with a loading screen that shows screenshots of EduVibe. It retries by itself and disappears as soon as the API answers. If the API is awake, nobody ever sees it.
