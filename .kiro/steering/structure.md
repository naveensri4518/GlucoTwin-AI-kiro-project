---
inclusion: always
---

# GlucoTwin AI — Repository Structure Steering

## Top-Level Layout

```
glucotwin-ai/
├── frontend/          # React + TypeScript dashboard
├── backend/           # Java 21 + Spring Boot REST API
├── ml-service/        # Python FastAPI — prediction, agents, RAG, MCP
├── infra/             # AWS CDK infrastructure as code
├── docs/              # Architecture Decision Records, diagrams, API specs
├── data/              # Synthetic datasets, seed scripts (NO real patient data)
├── scripts/           # Developer tooling (local setup, seed, lint, etc.)
├── docker-compose.yml # Full local stack
├── docker-compose.override.yml  # Local dev overrides (ports, hot-reload)
└── .kiro/             # Kiro steering, specs, hooks (do not delete)
```

---

## Frontend (`frontend/`)

```
frontend/
├── public/
├── src/
│   ├── app/               # App shell, router, global providers
│   ├── features/          # Feature slices (co-locate component, hook, type, test)
│   │   ├── dashboard/
│   │   ├── patient/
│   │   ├── prediction/
│   │   ├── simulation/    # What-if scenario UI
│   │   └── explanation/   # Risk explanation panel
│   ├── components/        # Shared, generic UI components (no business logic)
│   ├── hooks/             # Shared React hooks
│   ├── lib/               # API clients, utilities, constants
│   ├── types/             # Shared TypeScript types and Zod schemas
│   └── test/              # Test utilities, mocks, setup
├── index.html
├── vite.config.ts
├── tsconfig.json
└── package.json
```

### Frontend Rules
- Each feature folder is self-contained: component, styles, hooks, types, and tests live together.
- No feature may import directly from another feature's internal files. Use `lib/` or `types/` for shared contracts.
- All API calls go through a typed client in `lib/api/`. No raw `fetch` calls in components.

---

## Backend (`backend/`)

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/glucotwin/
│   │   │   ├── api/           # REST controllers, request/response DTOs
│   │   │   ├── application/   # Use cases / application services (orchestration)
│   │   │   ├── domain/        # Domain entities, value objects, domain services
│   │   │   │   ├── patient/
│   │   │   │   ├── twin/      # Digital Twin state aggregate
│   │   │   │   ├── prediction/
│   │   │   │   ├── simulation/
│   │   │   │   └── wearable/
│   │   │   ├── infrastructure/
│   │   │   │   ├── persistence/   # JPA repositories, entity mappers
│   │   │   │   ├── messaging/     # Redis pub/sub adapters
│   │   │   │   ├── ml/            # HTTP client for ML service
│   │   │   │   └── config/        # Spring configuration classes
│   │   │   └── GlucoTwinApplication.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       ├── application-prod.yml
│   │       └── db/migration/      # Flyway SQL migrations (V1__, V2__, ...)
│   └── test/
│       ├── java/com/glucotwin/
│       │   ├── api/               # Controller slice tests
│       │   ├── application/       # Use case unit tests
│       │   ├── domain/            # Domain logic unit tests
│       │   └── integration/       # Full-stack Testcontainers tests
│       └── resources/
│           └── test-data/         # SQL seed files for tests
├── build.gradle.kts
└── settings.gradle.kts
```

### Backend Architecture Layers (strictly ordered)
```
api → application → domain ← infrastructure
```
- `api` depends on `application`. Never on `domain` directly.
- `application` depends on `domain` interfaces. Never on `infrastructure` directly.
- `infrastructure` implements `domain` interfaces (ports).
- `domain` has **zero** dependencies on other layers.

---

## ML Service (`ml-service/`)

```
ml-service/
├── app/
│   ├── api/               # FastAPI routers (prediction, simulation, agents, mcp)
│   ├── agents/            # LangGraph agent graphs
│   ├── rag/               # Retriever, document loader, embedding pipeline
│   ├── models/            # Trained model loading, prediction pipeline
│   ├── features/          # Feature engineering from twin state
│   ├── simulation/        # What-if state mutation and re-prediction
│   ├── mcp/               # MCP server tool definitions
│   ├── schemas/           # Pydantic request/response models
│   └── config.py          # Settings (from environment only)
├── tests/
│   ├── unit/
│   ├── integration/
│   └── property/          # Hypothesis-based property tests
├── models/                # Serialised model artefacts (.joblib, .json)
├── data/                  # Synthetic training/validation data (gitignored if large)
├── notebooks/             # Exploratory analysis only — not production code
├── requirements.txt       # Pinned versions
├── requirements-dev.txt
├── Dockerfile
└── main.py
```

---

## Infrastructure (`infra/`)

```
infra/
├── lib/
│   ├── stacks/
│   │   ├── network-stack.ts
│   │   ├── database-stack.ts
│   │   ├── backend-stack.ts
│   │   ├── ml-stack.ts
│   │   └── frontend-stack.ts
│   └── constructs/        # Reusable CDK constructs
├── bin/
│   └── glucotwin.ts       # CDK app entry point
├── cdk.json
└── package.json
```

---

## Docs (`docs/`)

```
docs/
├── adr/                   # Architecture Decision Records (ADR-001.md, ...)
├── diagrams/              # C4 or PlantUML diagrams
├── api/                   # Exported OpenAPI specs
└── data-dictionary.md     # Field-level definitions for all data models
```

---

## Naming Conventions

### General
- Use `kebab-case` for file and directory names across all layers.
- Use `PascalCase` for class names (all languages).
- Use `camelCase` for variables and functions (Java, TypeScript).
- Use `snake_case` for variables and functions in Python.

### Database
- Table names: `snake_case`, plural (e.g., `patient_states`, `glucose_predictions`).
- Column names: `snake_case`.
- Migration files: `V{n}__{description}.sql` (e.g., `V1__create_patient_table.sql`).

### API Routes
- Use plural nouns: `/api/v1/patients`, `/api/v1/predictions`.
- Version prefix on all routes: `/api/v1/`.
- No verbs in paths (use HTTP methods for verbs).

### Tests
- Test file mirrors source file: `PatientService.java` → `PatientServiceTest.java`.
- Python: `patient_service.py` → `test_patient_service.py`.
- TypeScript: `PatientCard.tsx` → `PatientCard.test.tsx`.

---

## Configuration Organisation

- All environment-specific values come from **environment variables** or secrets manager. No hardcoded config values.
- `application.yml` holds structural defaults only. Secrets are never in `application.yml`.
- Local overrides go in `application-local.yml` (gitignored).
- Each service has a single `config.py` or `application.yml`; no scattered config loading.
- Docker Compose injects env vars; production uses AWS Secrets Manager + ECS task definitions.
