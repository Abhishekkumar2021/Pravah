# Pravah Web (EPIC-12)

React + Vite + TypeScript + Tailwind CSS v4. This package is the **alpha UI shell**: login layout, global navigation, dashboard, workflows, runs, and run detail with cancel wired to the gateway REST API.

## Scripts

```bash
cd web
npm ci
npm run dev      # http://localhost:5173 — proxies /api → gateway (default http://localhost:8080)
npm run build
npm run preview
```

## Environment

| Variable | Purpose |
|----------|---------|
| `VITE_PRAVAH_API_BASE` | Absolute API base (e.g. production gateway). If unset, the app uses same-origin `/api/...` (dev server proxy). |
| `VITE_DEV_PROXY_TARGET` | Override proxy target for `npm run dev` (see `vite.config.ts`). |

Copy `env.example` to `.env.local` for local overrides (gitignored).

## Live execution API (local)

1. Run the API gateway (and dependencies) so `GET/POST /api/v1/executions/...` is available.
2. `npm run dev` — Vite forwards `/api` to `VITE_DEV_PROXY_TARGET` (default `http://localhost:8080`).
3. Open **Runs →** pick a row or paste a real execution UUID in the URL (`/app/runs/{id}`).
4. Open **Dev token**, paste a JWT the gateway accepts, **Save & reload** (stored as `localStorage.pravah.devBearerToken`).

Cancel uses `POST /api/v1/executions/{id}/cancel` (US-02.04).

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

GraphQL read model (ADR-033) is not implemented yet; pages use mock data or REST where noted.
