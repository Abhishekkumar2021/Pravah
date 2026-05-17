# Pravah Web (EPIC-12)

React + Vite + TypeScript + Tailwind CSS v4. This package is the **alpha UI shell**: login layout, global navigation, dashboard, workflows, runs, and run detail with cancel wired to the gateway REST API.

Backend capabilities and gaps: [Implementation Status](../docs/IMPLEMENTATION_STATUS.md).

## Scripts

```bash
cd web
npm ci
npm run dev      # http://localhost:5173 — proxies /api → gateway (default http://localhost:8080)
npm run lint     # TypeScript check (same as CI)
npm run test     # Vitest + Testing Library (UI integration tests)
npm run build
npm run preview
```

## Environment

| Variable | Purpose |
|----------|---------|
| `VITE_PRAVAH_API_BASE` | Absolute API base (e.g. production gateway). If unset, the app uses same-origin `/api/...` (dev server proxy). |
| `VITE_PRAVAH_PROJECT_ID` | Default **project UUID** for `GET /api/v1/pipelines?projectId=…` (workflows list, dashboard). Overridable in the UI via **Project scope** (stored as `localStorage.pravah.defaultProjectId`). |
| `VITE_DEV_PROXY_TARGET` | Override proxy target for `npm run dev` (see `vite.config.ts`). |

Copy `env.example` to `.env.local` for local overrides (gitignored).

## Live execution API (local)

1. Run the API gateway (and dependencies) so `GET/POST /api/v1/executions/...` is available.
2. `npm run dev` — Vite forwards `/api` to `VITE_DEV_PROXY_TARGET` (default `http://localhost:8080`).
3. Sign in at `/login` (seeded user after `make local-seed`: `dev@localhost.pravah` / `PravahDev1!`). The access token is stored as `localStorage.pravah.accessToken`.
4. Open **Workflows** or **Dashboard** — set **Project scope** (or `VITE_PRAVAH_PROJECT_ID`) so pipeline lists resolve.
5. Open **Runs** and pick a row, or navigate to `/app/runs/{executionId}`.

Cancel uses `POST /api/v1/executions/{id}/cancel` (US-02.04).

## Real-time execution updates (WebSocket)

1. Same signed-in session as REST (`pravah.accessToken`).
2. Vite proxies `/ws` to the gateway → execution-service (`/ws/v1/executions?access_token=…`).
3. Run detail, runs list, and dashboard subscribe while runs are pending/running and refetch on `execution.updated` frames.

Set `PRAVAH_WS_ALLOWED_ORIGINS` on execution-service if the SPA is not served from `http://localhost:5173`.

## E2E tests (Playwright)

```bash
npm run test:e2e
```

- CI uses `CI=1` (build + preview on port 5173).
- Locally, if `npm run dev` is already on 5173, Playwright reuses it (`reuseExistingServer`).
- Otherwise set `PLAYWRIGHT_PORT` (e.g. `5199`) when port 5173 is busy.

## Product mapping

| Area | Stories |
|------|---------|
| Login split layout | US-12.01 |
| Sidebar + shell | US-12.02 |
| Dashboard cards | US-12.03 |
| Workflow table | US-12.04 |
| Workflow detail tabs | US-12.05 |
| Run list | US-12.07 |
| Run detail + cancel | US-12.08 / US-02.04 |
| Run detail stage output panel | US-02.10 (`RunJobOutputPanel`, `GET /api/v1/executions/{id}` job `output`) |
| Real-time run status | US-12.10 / US-02.02 |

GraphQL read model (ADR-033) is not implemented yet; pages use REST where noted.

## Design system (`src/components/ui/`)

Use `Button`, `Input`, `Select`, `Checkbox`, `PasswordInput`, and `IconButton` for actions and form fields so styling stays consistent. Do not paste one-off field styles in pages — extend primitives with `className` + `cn()` instead.
