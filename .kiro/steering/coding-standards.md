---
inclusion: always
---

# GlucoTwin AI — Coding Standards Steering

## Governing Principle

Code is read far more often than it is written. Optimise for clarity, consistency, and correctness. These standards apply to all production code in every service. Deviations require an explicit comment explaining why, not a silent exception.

---

## 1. Clean Architecture Principles

These apply across all services regardless of language.

- **Dependency rule.** Dependencies point inward only: outer layers (API, infrastructure) depend on inner layers (application, domain). The domain layer has no external dependencies.
- **Separate concerns.** Data transfer objects (DTOs) are not domain objects. Map at the boundary.
- **Single responsibility.** A class or module does one thing. If you need the word "and" to describe what it does, split it.
- **Explicit over implicit.** Prefer explicit configuration and wiring over magic (auto-scan, reflection-heavy frameworks) where clarity is more important than brevity.
- **No anemic domain models.** Domain entities contain business logic, not just getters and setters.

---

## 2. Java / Spring Boot Conventions

### Language
- Use Java 21 features where they improve readability: records, sealed classes, pattern matching, text blocks, virtual threads.
- Prefer `Optional<T>` over `null` returns from service methods. Never return `null` from a public method.
- Use `var` for local variable inference only when the type is obvious from the right-hand side.

### Spring Boot
- Configuration beans go in `infrastructure/config/`. No `@Bean` methods scattered in service classes.
- Use constructor injection everywhere. Field injection (`@Autowired`) is forbidden — it hides dependencies.
- Keep controllers thin: validate input, delegate to application service, map response. No business logic in controllers.
- Use `@Validated` + Bean Validation on request DTOs. Never validate manually in the controller body.
- Pagination defaults: `page=0`, `size=20`, `maxSize=100`. Enforce max page size.

### Records and DTOs
```java
// Request DTO
public record CreatePatientRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotNull @Past LocalDate dateOfBirth
) {}

// Response DTO
public record PredictionResponse(
    UUID predictionId,
    double spikeProbability,
    double confidenceLow,
    double confidenceHigh,
    List<RiskDriver> topRiskDrivers,
    Instant predictedAt,
    String modelVersion,
    DataLabel dataLabel   // always PREDICTED
) {}
```

### Error Handling
- Use a global `@RestControllerAdvice` for exception mapping. No try-catch blocks in controllers.
- Define a hierarchy of typed application exceptions (`GlucoTwinException` base, then specific subtypes).
- Map domain exceptions to HTTP status codes in the advice — not in service code.
- Error response body: `{ "error": "PATIENT_NOT_FOUND", "message": "...", "timestamp": "..." }`. No stack traces.

### Logging (Java)
- Use SLF4J with Logback. No `System.out.println`.
- Log at the correct level:
  - `DEBUG` — internal state useful during development.
  - `INFO` — significant business events (patient loaded, prediction completed).
  - `WARN` — recoverable issues (data quality warning, retry).
  - `ERROR` — failures requiring attention (prediction failed, DB unreachable).
- Structured JSON logging in all non-local environments (Logback JSON encoder).
- Never log sensitive fields (patientId in cleartext, glucose values, medication names). Use opaque references.

---

## 3. Python Conventions

### Language
- Target Python 3.11+. Use type hints on every function signature — no untyped public functions.
- Run `mypy --strict` on all modules under `app/` (not `notebooks/`).
- Use `dataclasses` or Pydantic models for structured data. No plain dicts as function parameters.
- Prefer `pathlib.Path` over `os.path`.

### FastAPI
- Define Pydantic request and response schemas in `app/schemas/`. Do not define them inline in router files.
- Use dependency injection (`Depends`) for database sessions, auth, and configuration.
- All endpoints return typed Pydantic response models. No `dict` or `Any` return types on routes.
- Use `HTTPException` for client errors, not bare `raise Exception`.

```python
# Good
@router.post("/predict", response_model=PredictionResponse)
async def predict_spike(
    request: PredictionRequest,
    service: PredictionService = Depends(get_prediction_service),
) -> PredictionResponse:
    return await service.predict(request)
```

### Error Handling (Python)
- Define custom exception classes in `app/exceptions.py`.
- Use FastAPI exception handlers registered in `main.py` — not try/except in route functions.
- Log full tracebacks server-side; return structured error responses to clients.

### Logging (Python)
- Use the standard `logging` module with JSON formatter in production.
- Configure once in `config.py`; use `logging.getLogger(__name__)` in each module.
- Same sensitivity rules as Java: no PII in logs.

---

## 4. React / TypeScript Conventions

### TypeScript
- `strict: true` in `tsconfig.json`. No `any` types in production code.
- Use `type` for data shapes, `interface` for extension/augmentation.
- Zod schemas are the single source of truth for API response types. Generate TypeScript types from Zod with `z.infer<>`.

### React
- Functional components only. No class components.
- Co-locate component, styles, hooks, types, and tests in the feature folder.
- Custom hooks (`use*`) for all stateful logic — no logic directly in JSX.
- Keep components small (< 150 lines). Extract sub-components rather than growing a single file.
- Avoid `useEffect` for data fetching — use TanStack Query instead.

```typescript
// Good
function SpikeProbabilityBadge({ probability }: { probability: number }) {
  const riskLevel = useRiskLevel(probability);
  return (
    <Badge variant={riskLevel} aria-label={`Spike risk: ${riskLevel}`}>
      {(probability * 100).toFixed(1)}%
    </Badge>
  );
}
```

### Accessibility
- Every interactive element has an accessible name (label, aria-label, or aria-labelledby).
- Use semantic HTML elements (`<button>`, `<nav>`, `<main>`, `<section>`) over `<div>` with click handlers.
- Colour is never the only conveyor of meaning (always pair with text or icon).
- Data label badges (`OBSERVED`, `PREDICTED`, `SIMULATED`) must include `role="status"` or visible text — never colour alone.

### Error Handling (Frontend)
- Use React Error Boundaries around major feature areas.
- TanStack Query error states render a user-facing message — never expose API error details.
- Form validation errors are displayed inline, adjacent to the relevant field.

---

## 5. REST API Conventions

- All routes versioned: `/api/v1/`.
- Plural nouns for resources: `/patients`, `/predictions`, `/simulations`.
- HTTP methods map to operations: `GET` read, `POST` create/action, `PUT` full replace, `PATCH` partial update, `DELETE` remove.
- Use `201 Created` with a `Location` header for resource creation.
- Use `204 No Content` for successful deletes.
- Use `202 Accepted` for async operations (e.g., triggering a simulation).
- Pagination: `?page=0&size=20`. Response includes `{ content: [], page: { number, size, totalElements, totalPages } }`.
- Filtering/sorting: `?sort=createdAt,desc&filter=status:ACTIVE`.
- Date-time: ISO 8601 UTC (`2026-10-04T12:00:00Z`) in all request and response bodies.

---

## 6. Documentation

- **Public API methods** in Java: Javadoc on all `public` methods in `domain/` and `application/`. Skip trivial getters.
- **Python modules:** Module-level docstring explaining purpose. Function docstrings for public functions in `app/models/` and `app/features/`.
- **TypeScript:** JSDoc on exported functions and hooks that are non-obvious.
- **Architecture decisions:** Document non-obvious design decisions in `docs/adr/` (Architecture Decision Records). Use the format: Context → Decision → Consequences.
- **No commented-out code** in committed files. Use ADRs or git history for context.
- **TODO comments** must include a ticket/issue reference: `// TODO(GT-123): replace with streaming endpoint`.

---

## 7. General Code Quality Rules

- Maximum function/method length: **40 lines**. If it needs more, break it down.
- Maximum class length: **300 lines**. If it needs more, decompose responsibilities.
- No magic numbers or strings. Define named constants or enums.
- Immutability by default: prefer `final` fields (Java), `val`/`const`, frozen Pydantic models, `readonly` TypeScript properties.
- No silent failures. Every caught exception is either re-thrown, logged, or converted to a domain error — never swallowed with an empty catch block.
- Code formatters are mandatory and run in CI:
  - Java: `google-java-format` or `Spotless`
  - Python: `black` + `isort` + `ruff`
  - TypeScript: `Prettier` + `ESLint`
  - Configuration files live in the repo root (`pyproject.toml`, `.prettierrc`, `.eslintrc`).
