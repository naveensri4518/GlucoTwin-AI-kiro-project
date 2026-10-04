# GlucoTwin AI — Doctor Dashboard

React + TypeScript + Vite frontend for the GlucoTwin Clinical Decision Support system.

> **Research and Decision Support Only.**
> Not a diagnostic system. All predictions are probabilistic estimates. Always apply clinical judgment.

## Running locally

**Prerequisites:** Node.js ≥ 20, backend running on `http://localhost:8080`

```bash
cd frontend

# 1. Install dependencies (first time only)
npm install

# 2. Start the dev server
npm run dev
```

The dashboard opens at **http://localhost:3000**

### Environment

Copy `.env.example` to `.env.local` if you need to change the backend URL:

```bash
cp .env.example .env.local
# Edit VITE_API_BASE_URL if your backend runs on a different port
```

Default `.env.local`:
```
VITE_API_BASE_URL=http://localhost:8080
```

### Authentication (dev mode)

When the backend has no `JWT_PUBLIC_KEY` configured (prototype mode), the API client
automatically sends `X-Dev-Auth: ROLE_CLINICIAN` on every request. No login screen is needed.

### Build

```bash
npm run build      # production build → dist/
npm run typecheck  # TypeScript check only (no emit)
npm run preview    # preview the production build locally
```

## Current routes

| Route | Page |
|---|---|
| `/` | Redirects to `/patients` |
| `/patients` | Patient list (Phase 3) |
| `/patients/:patientId` | Patient detail — Digital Twin, predictions, CGM chart (Phase 3+) |
