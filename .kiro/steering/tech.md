---
inclusion: always
---

# GlucoTwin AI — Technology Stack Steering

## Guiding Principle

Choose boring, well-supported technology where correctness and maintainability matter. Introduce specialist tools (LangGraph, RAG, MCP) only where they directly serve a product capability. Never add a dependency without a clear owner and an identified test strategy.

---

## Runtime Versions (pinned minimums)

| Layer | Technology | Version |
|---|---|---|
| Frontend | Node.js | 20 LTS |
| Backend | Java | 21 (LTS, virtual threads enabled) |
| ML service | Python | 3.11 |
| Container runtime | Docker Engine | 25+ |

---

## Frontend

| Concern | Choice | Rationale |
|---|---|---|
| Framework | React 18 | Concurrent rendering, large ecosystem |
| Language | TypeScript 5 | Type safety across the UI contract |
| Build | Vite | Fast HMR, ES module native |
| State management | Zustand | Lightweight; avoid Redux for this scale |
| Data fetching | TanStack Query (React Query) | Cache, background refresh, loading states |
| Charts | Recharts | Composable, TypeScript-native |
| Component library | shadcn/ui + Tailwind CSS | Accessible primitives, no lock-in |
| Forms | React Hook Form + Zod | Schema-validated forms |
| Testing | Vitest + React Testing Library | Co-located with Vite toolchain |

---

## Backend (Java / Spring Boot)

| Concern | Choice | Rationale |
|---|---|---|
| Framework | Spring Boot 3.3 | Auto-configuration, Spring Security, Actuator |
| HTTP server | Embedded Tomcat (default) | Sufficient for REST; swap to Netty only if reactive is needed |
| Concurrency | Virtual threads (Project Loom, Java 21) | High-throughput I/O without reactive complexity |
| ORM | Spring Data JPA + Hibernate 6 | Standard persistence layer |
| Migration | Flyway | Versioned, repeatable DB migrations |
| Validation | Jakarta Bean Validation (Hibernate Validator) | Declarative input constraints |
| API docs | SpringDoc OpenAPI 3 | Auto-generated, stays in sync with code |
| Testing | JUnit 5 + Mockito + Testcontainers | Unit, integration, DB-against-real-container |
| Property-based testing | jqwik | Java-native property-based tests |
| Build | Gradle (Kotlin DSL) | Incremental builds, good plugin ecosystem |

---

## ML Service (Python)

| Concern | Choice | Rationale |
|---|---|---|
| Web framework | FastAPI | Async, auto OpenAPI, type hints |
| ML core | scikit-learn + XGBoost | Interpretable, well-tested on tabular data |
| Time-series features | tsfresh | Automated feature extraction from CGM streams |
| Explainability | SHAP | Feature importance for predictions |
| Data processing | pandas + numpy | Standard tabular pipeline |
| AI orchestration | LangChain + LangGraph | Agent graph, tool routing, memory |
| LLM provider | OpenAI API (gpt-4o) | Explanation generation, what-if narrative |
| RAG vector store | pgvector (PostgreSQL extension) | Reuse existing DB; no extra infra |
| RAG embeddings | OpenAI `text-embedding-3-small` | Cost-effective, sufficient quality |
| MCP integration | MCP Python SDK | Tool exposure to AI agents |
| Testing | pytest + Hypothesis | Unit tests and property-based tests |
| Type checking | mypy | Strict mode on all production modules |

---

## Data Storage

| Store | Technology | Purpose |
|---|---|---|
| Primary DB | PostgreSQL 16 | Patient state, EHR data, predictions, audit log |
| Vector extension | pgvector | RAG embeddings — no separate vector DB |
| Cache / pub-sub | Redis 7 | Wearable stream buffering, session cache, rate limiting |
| Object storage | AWS S3 (or MinIO locally) | ML model artefacts, raw ingested files |

---

## Infrastructure & Cloud

| Concern | Choice |
|---|---|
| Containerisation | Docker + Docker Compose (local dev) |
| Container orchestration | AWS ECS Fargate (production target) |
| Cloud | AWS |
| CI/CD | GitHub Actions |
| Secrets management | AWS Secrets Manager (prod) / `.env` files (local, gitignored) |
| Observability | AWS CloudWatch + OpenTelemetry (traces, metrics, logs) |
| API gateway | AWS API Gateway or Spring Cloud Gateway |
| IaC | AWS CDK (TypeScript) |

---

## API Style

- **REST** over HTTPS for all external-facing endpoints.
- **OpenAPI 3.1** contract published by the backend at `/api/docs`.
- **Server-Sent Events (SSE)** for streaming wearable updates to the dashboard.
- **Internal** ML service ↔ backend communication: REST (JSON).
- **MCP** tool protocol for AI agent ↔ tool boundaries.

---

## AI / ML Stack Summary

```
FastAPI (Python ML service)
  └── LangGraph agent graph
        ├── LangChain tools (RAG retriever, EHR lookup, prediction runner)
        ├── MCP server (exposes tools to external agents)
        ├── OpenAI GPT-4o (LLM for explanations)
        └── pgvector RAG (clinical guidelines, literature)
```

---

## Development Principles

1. **Reproducible builds.** Pin all dependency versions. No open ranges (`^`, `~`, `*`) in production manifests.
2. **Contracts first.** Define OpenAPI specs and data schemas before writing implementation code.
3. **Fail fast.** Validate inputs at the boundary of every service. Never pass invalid data deeper.
4. **Observability by default.** Every service emits structured logs (JSON), metrics, and distributed traces from day one.
5. **Local-first dev.** `docker compose up` must bring up the full stack locally with synthetic data. No cloud dependency for development.
6. **No dead code.** Remove unused dependencies, routes, and models before merging.
