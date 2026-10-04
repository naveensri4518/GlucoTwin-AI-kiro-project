# Tasks: Two-Hour Glucose Spike Prediction

## Overview

These tasks translate `requirements.md` and `design.md` into concrete, implementation-ready work items. They are ordered by dependency: each task may only begin when its listed prerequisites are complete. No task implements speculative or future features — LangGraph, MCP, RAG, and what-if simulation are extension points prepared but not built here.

---

## Dependency Graph (summary)

```
TASK-001 (project scaffold)
    │
    ├─▶ TASK-002 (domain model)
    │       │
    │       ├─▶ TASK-003 (port interfaces)
    │       │       │
    │       │       ├─▶ TASK-004 (DB schema + Flyway)
    │       │       │       │
    │       │       │       ├─▶ TASK-005 (JPA repositories)
    │       │       │       │       │
    │       │       │       │       ├─▶ TASK-008 (use cases)
    │       │       │       │       │       │
    │       │       │       │       │       ├─▶ TASK-010 (REST controllers)
    │       │       │       │       │       └─▶ TASK-011 (background jobs)
    │       │       │       │       └─▶ TASK-012 (integration tests — backend)
    │       │       │       └─▶ TASK-009 (security layer)
    │       │       │
    │       │       └─▶ TASK-006 (Redis stream)
    │       │               │
    │       │               └─▶ TASK-008
    │       │
    │       └─▶ TASK-007 (ML service scaffold)
    │               │
    │               ├─▶ TASK-013 (feature engineering)
    │               │       │
    │               │       └─▶ TASK-014 (XGBoost model + SHAP + CI)
    │               │               │
    │               │               └─▶ TASK-015 (ML FastAPI endpoints)
    │               │                       │
    │               │                       └─▶ TASK-008 (Java ML client)
    │               └─▶ TASK-016 (ML unit + property tests)
    │
    ├─▶ TASK-017 (observability)
    ├─▶ TASK-018 (safety constraint tests)
    ├─▶ TASK-019 (load / performance tests)
    └─▶ TASK-020 (Docker Compose local stack)
```

---

## TASK-001 — Project Scaffold and Build Configuration

**Depends on:** nothing  
**Satisfies:** tech.md (build tooling), structure.md (repo layout)

### What to build
- Create the top-level monorepo directory structure as defined in `structure.md`:
  - `frontend/`, `backend/`, `ml-service/`, `infra/`, `docs/`, `data/`, `scripts/`
- Initialise the **backend** as a Gradle (Kotlin DSL) project:
  - Java 21, Spring Boot 3.3
  - Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `spring-boot-starter-data-redis`, `springdoc-openapi-starter-webmvc-ui`, `flyway-core`, `postgresql`, `jqwik`, `testcontainers` (postgresql, redis)
  - `settings.gradle.kts` with project name `glucotwin-backend`
  - `GlucoTwinApplication.java` entry point with virtual threads enabled: `spring.threads.virtual.enabled=true`
  - `application.yml` structural skeleton (all secrets via `${ENV_VAR}`)
  - `application-local.yml` (gitignored template)
  - `.env.example` with all required variables and comments
- Initialise the **ml-service** as a Python project:
  - `requirements.txt` (pinned): `fastapi`, `uvicorn`, `pydantic`, `scikit-learn`, `xgboost`, `shap`, `tsfresh`, `pandas`, `numpy`, `joblib`, `pytest`, `hypothesis`, `httpx`, `pytest-asyncio`, `mypy`
  - `requirements-dev.txt`: `black`, `isort`, `ruff`, `mypy`
  - `main.py` FastAPI app entry point
  - `app/config.py` — settings loaded from environment only (Pydantic `BaseSettings`)
  - `pyproject.toml` — black, isort, ruff, mypy configuration
- Create top-level `.gitignore` covering: `.env`, `*.env`, `__pycache__`, `.mypy_cache`, `build/`, `.gradle/`, `node_modules/`, `ml-service/models/*.joblib`, `ml-service/data/`
- Create `docs/data-dictionary.md` skeleton with section headers for all domain entities

### Acceptance criteria
- `./gradlew build` in `backend/` compiles and passes with zero source files (skeleton only)
- `python -m pytest ml-service/tests/` passes with zero test files (skeleton only)
- `.env.example` exists and contains placeholder values for all secrets referenced in `application.yml`
- Directory structure matches `structure.md` exactly

---

## TASK-002 — Domain Model Implementation

**Depends on:** TASK-001  
**Satisfies:** REQ-003, REQ-005, REQ-013, REQ-017 (partial), design §1

### What to build

**Package:** `com.glucotwin.domain`

- **Value objects and enumerations:**
  - `PatientId`, `PredictionId` — typed UUID wrappers (records)
  - `TwinStatus`, `RiskCategory`, `DataProvenance`, `SleepStage`, `ActivityLevel`, `PredictionStatus`, `Sex` enums
  - `StaticLayer` record (all fields as per design §1.3)
  - `DynamicLayer` record (all fields as per design §1.4), including `Set<String> dataQualityWarnings`
  - `ConfidenceInterval` record (`double low`, `double high`) with invariant: `low ≤ high`, both in [0,1]
  - `ContributingFactor` record (`String factorName`, `double contribution`, `RiskDirection direction`)
  - `Medication` record (`String name`, `String dose`, `String frequency`)
  - `LabResult` record (`String type`, `double value`, `String unit`, `Instant recordedAt`)
  - `FeatureVector` record (all fields as per design §1.7)
  - `TwinStateSnapshot` record (all fields as per design §1.5) with static factory `from(DigitalTwinState)`
  - `PredictionResult` record (design §2.1)

- **`DigitalTwinState` aggregate** (`com.glucotwin.domain.twin`):
  - Constructor: `create(PatientId)` — sets version=1, status=INITIALISED
  - `applyEhrRecord(EhrRecord)` — updates static layer, increments version, sets lastUpdatedAt
  - `applyWearableEvent(WearableEvent)` — updates dynamic layer + CGM history buffer (max 12 entries), increments version, transitions status INITIALISED→ACTIVE or STALE→ACTIVE
  - `markStale()` — sets status=STALE; no-op if already STALE or ARCHIVED
  - `archive()` — sets status=ARCHIVED; throws `TwinStateArchivedError` if called again
  - `snapshot()` — returns immutable `TwinStateSnapshot`
  - All mutations enforce the version increment invariant

- **`PredictionRecord` entity** (`com.glucotwin.domain.prediction`):
  - Factory: `pending(PatientId, int twinStateVersion, String modelVersion)`
  - `complete(PredictionResult, RiskCategory, List<String> qualityWarnings)`
  - `fail(String reason)`
  - Invariant: once COMPLETED or FAILED, no further mutation

- **`RiskCategoryDeriver`** (`com.glucotwin.domain.prediction`):
  - `derive(double probability, RiskThresholds thresholds) → RiskCategory`
  - `RiskThresholds` is a value object loaded from config (not hardcoded)
  - Boundary rule: value exactly at threshold falls into the upper category

- **`EhrRecord`** and **`WearableEvent`** domain event records

### Unit tests to write
- `DigitalTwinStateTest`:
  - version increments on every apply call
  - status transitions follow the defined FSM
  - archived state throws on mutation
  - `snapshot()` returns an equal-value immutable copy
  - CGM history buffer caps at 12 entries (oldest evicted)
- `PredictionRecordTest`:
  - PENDING → COMPLETED transition stores all fields
  - PENDING → FAILED stores failure reason
  - COMPLETED record rejects further mutation
- `RiskCategoryDeriverTest`:
  - All four categories with mid-range values
  - All three boundary values (0.30, 0.60, 0.85) → upper category
  - Custom thresholds respected
- `ConfidenceIntervalTest`:
  - low > high throws `IllegalArgumentException`
  - values outside [0,1] throw

### Acceptance criteria
- All domain classes have zero dependencies on Spring, JPA, or any framework
- All unit tests pass with `./gradlew test`
- `ConfidenceInterval` invariants enforced in constructor

---

## TASK-003 — Port Interface Definitions

**Depends on:** TASK-002  
**Satisfies:** REQ-016, design §2

### What to build

**Package:** `com.glucotwin.domain` (sub-packages by domain)

Define all port interfaces — no implementations yet:

- `DigitalTwinRepository` (design §2)
- `PredictionRepository` (design §2) including `findByModelVersion`
- `PredictionModelPort` (design §2) — `predict(TwinStateSnapshot, FeatureVector)` and `getModelVersion()`
- `WearableEventPublisher` (design §2)
- `TwinStateEventPublisher` (design §2)
- `EhrRepository` — `save(EhrRecord)`, `findByPatientId(PatientId)`
- `PatientRepository` — `save(Patient)`, `findById(PatientId)`, `existsById(PatientId)`

Also define:
- `MockPredictionModel` in `src/test/java` — implements `PredictionModelPort`:
  - Configurable fixed probability (default 0.5)
  - Configurable exception to throw on `predict()`
  - Records all calls made (for test assertion)

### Acceptance criteria
- All interfaces compile with no implementation
- `MockPredictionModel` can be configured via constructor to return any probability or throw any exception
- `MockPredictionModel` is in the test source set, not production

---

## TASK-004 — Database Schema and Flyway Migrations

**Depends on:** TASK-003  
**Satisfies:** REQ-014, design §7

### What to build

Create Flyway migration SQL files under `backend/src/main/resources/db/migration/`:

- `V1__create_patients_table.sql`
- `V2__create_patient_ehr_table.sql`
- `V3__create_digital_twin_states_table.sql` — include `version` column for optimistic locking
- `V4__create_glucose_predictions_table.sql`
- `V5__create_wearable_events_raw_table.sql`
- `V6__create_dead_letter_events_table.sql`
- `V7__create_prediction_audit_log_table.sql`
- `V8__add_prediction_indexes.sql`

Each migration must exactly match the schema in design §7.1. Include:
- All column types and constraints as specified
- `NOT NULL` constraints where defined
- `DEFAULT` values where defined
- `REFERENCES` foreign key constraints
- All indexes from design §7.1

Also create `data/synthetic/seed_patients.sql` and `data/synthetic/seed_wearable_events.sql` with 5 synthetic patients and 20 wearable events per patient. Each seed record includes `data_source = 'SYNTHETIC'`.

### Acceptance criteria
- `./gradlew flywayMigrate` against a local PostgreSQL instance applies all 8 migrations without error
- All tables and indexes exist as specified in design §7.1
- Seed SQL files execute without error against the migrated schema
- Every seed record has `data_source = 'SYNTHETIC'`
- A Testcontainers integration test confirms migrations run cleanly on a fresh PostgreSQL 16 container

---

## TASK-005 — JPA Repository Implementations

**Depends on:** TASK-004  
**Satisfies:** REQ-014, design §7.3

### What to build

**Package:** `com.glucotwin.infrastructure.persistence`

- `DigitalTwinStateJpaEntity` — JPA `@Entity` mapping to `digital_twin_states`:
  - `@Version` field for optimistic locking
  - `cgmHistory` mapped as `@Column(columnDefinition = "jsonb")` using Jackson converter
  - `dataQualityFlags` mapped as `String[]`
- `GlucosePredictionJpaEntity` — JPA `@Entity` mapping to `glucose_predictions`:
  - `contributingFactors`, `featureVector` as JSONB columns
  - `dataQualityWarnings` as `String[]`
- `PatientEhrJpaEntity`, `WearableEventJpaEntity`, `DeadLetterEventJpaEntity`
- Spring Data `JpaRepository` interfaces: `DigitalTwinStateJpaRepository`, `GlucosePredictionJpaRepository`, `PatientEhrJpaRepository`

- **Adapter implementations** (implement domain ports):
  - `JpaDigitalTwinRepository implements DigitalTwinRepository`:
    - `findByPatientId` → maps JPA entity to domain object
    - `save` → catches `OptimisticLockingFailureException`, retries once, then throws `OptimisticLockRetryExhaustedException`
    - `findStaleAfter(Duration)` → custom JPQL query on `wearable_event_at`
  - `JpaPredictionRepository implements PredictionRepository`:
    - `findByPatientIdAndTimeRange` → JPQL with `BETWEEN`
    - `findByModelVersion` → JPQL with model_version filter
  - `JpaEhrRepository implements EhrRepository`

- **Mappers**: `DigitalTwinStateMapper`, `PredictionRecordMapper` — bidirectional domain ↔ JPA entity conversion. No MapStruct; hand-written static methods.

### Acceptance criteria
- Testcontainers integration test: save a `DigitalTwinState`, retrieve it, confirm all fields round-trip correctly including JSONB fields
- Concurrent update test: two threads update the same `DigitalTwinState` simultaneously; one succeeds, one triggers retry; no lost update
- `findStaleAfter(30 minutes)` returns only twins whose `wearable_event_at` is > 30 minutes ago

---

## TASK-006 — Redis Stream Integration

**Depends on:** TASK-003  
**Satisfies:** REQ-002, REQ-024, design §8

### What to build

**Package:** `com.glucotwin.infrastructure.messaging`

- `RedisWearableEventPublisher implements WearableEventPublisher`:
  - Serialises `WearableEvent` to JSON (including `traceId` from MDC)
  - Publishes to stream key `glucotwin:wearable-events:{patientId}` via `RedisTemplate`
  - Publishes within 200ms (async, non-blocking from controller perspective)

- `WearableEventConsumer` (Spring `@StreamListener` or `StreamMessageListenerContainer`):
  - Consumer group: `twin-state-updater`
  - On message: deserialise → call `TriggerTwinUpdateAndPredictionUseCase` (internal orchestrator, see TASK-008)
  - On success: ACK the message
  - On failure: retry with exponential backoff (1s, 4s, 16s), max 3 attempts
  - After 3 failures: write to `dead_letter_events` table, emit `DEAD_LETTER_EVENT` log at ERROR

- `RedisConfiguration` (`@Configuration`):
  - Jackson `RedisSerializer` for event serialisation
  - Connection factory configured from `${REDIS_URL}`, `${REDIS_PASSWORD}` env vars

- `DeadLetterEventRepository` (simple JPA repository for `dead_letter_events` table)

### Acceptance criteria
- Integration test: publish a `WearableEvent` to Redis; consumer picks it up within 1 second
- Failure test: consumer always throws; after 3 retries, event appears in `dead_letter_events` table
- Backoff timing test: assert retry delays are approximately 1s, 4s, 16s (allow ±20% tolerance)
- TraceId from publisher appears in the consumed message

---

## TASK-007 — ML Service Project Scaffold and Configuration

**Depends on:** TASK-001  
**Satisfies:** tech.md (Python stack), structure.md (ml-service layout), design §9

### What to build

**Location:** `ml-service/`

- Complete `app/` package structure:
  - `app/api/` — FastAPI router modules (empty stubs)
  - `app/models/` — model loading and port definition (empty stubs)
  - `app/features/` — feature engineering (empty stub)
  - `app/schemas/` — Pydantic request/response models
  - `app/config.py` — `Settings(BaseSettings)` loading all config from env
  - `app/exceptions.py` — custom exception hierarchy
  - `app/logging_config.py` — JSON logging setup (structlog or standard logging + JSON formatter)
- `main.py` — FastAPI app with:
  - Exception handlers registered for all custom exceptions
  - Startup event: load model artefact, verify it loads without error
  - Health endpoint: `GET /health` → `{ "status": "ok", "modelVersion": "<version>" }`
  - Model info endpoint: `GET /model/info`
  - Internal token middleware: rejects requests without valid `X-Internal-Token` header (except `/health`)
- `Dockerfile` for ml-service:
  - Multi-stage build (builder + runtime)
  - Non-root user
  - Model artefact copied from `models/` or downloaded from S3 on startup (controlled by `MODEL_SOURCE` env var: `local` | `s3`)
- `tests/` structure:
  - `tests/unit/`
  - `tests/integration/`
  - `tests/property/`
  - `tests/conftest.py` — shared fixtures

**Pydantic schemas** (all in `app/schemas/`):
- `TwinStateSnapshotDTO` — mirrors `TwinStateSnapshot` Java record
- `WearableEventDTO`
- `PredictionRequestDTO`
- `PredictionResponseDTO` (full output schema matching design §6.2)
- `ErrorResponseDTO`

### Acceptance criteria
- `uvicorn main:app --reload` starts without error
- `GET /health` returns `200` with JSON body
- `GET /model/info` returns model metadata
- Request without `X-Internal-Token` returns `401`
- All Pydantic schemas pass `mypy --strict` with zero errors
- `pytest tests/` passes (zero tests is OK at this stage)

---

## TASK-008 — Application Use Cases

**Depends on:** TASK-005, TASK-006, TASK-007 (ML client stub)  
**Satisfies:** REQ-001 through REQ-008, REQ-011, REQ-015, design §3

### What to build

**Package:** `com.glucotwin.application`

Implement all six use cases. Each use case is a `@Service` class with constructor-injected port interfaces. No Spring-specific annotations inside use case logic — only `@Service` on the class.

#### `IngestEhrUseCase`
- Validate `EhrRecord` fields (delegate to domain validation)
- Assert patient exists (`PatientRepository.existsById`)
- Persist `EhrRecord`
- Apply to `DigitalTwinState` static layer (create twin if first EHR upload)
- Persist updated `DigitalTwinState`
- Does NOT trigger prediction (REQ-004)

#### `IngestWearableEventUseCase`
- Validate `WearableEvent` (physiological ranges as per REQ-010)
- Assert patient exists
- Apply event to `DigitalTwinState` → new version
- Persist `DigitalTwinState`
- Publish event to Redis via `WearableEventPublisher`
- Returns `eventId`

#### `TriggerPredictionUseCase`
- Assert `DigitalTwinState` is not ARCHIVED
- Assert `glucoseReading` is present (throw `GlucoseReadingRequiredException` if absent — REQ-011)
- Take `TwinStateSnapshot` inside `@Transactional(readOnly=true)` block
- Create `PredictionRecord` (PENDING), persist
- Call `PredictionModelPort.predict(snapshot, featureVector)`
  - The `FeatureVector` is built by the ML service from the snapshot; the Java side passes the full `TwinStateSnapshot` JSON to the ML service and receives back a `PredictionResult`
  - On failure: update record to FAILED, persist, rethrow mapped exception
- Derive `RiskCategory` via `RiskCategoryDeriver`
- Call `PredictionRecord.complete(...)`, persist
- Write audit log entry (patientId hashed)
- Return `PredictionId`

#### `GetDigitalTwinStateUseCase`
- Load `DigitalTwinState` by `patientId`
- Map to `DigitalTwinStateResponse` DTO
- Return

#### `GetPredictionUseCase`
- Load `PredictionRecord` by `predictionId`
- Verify caller has access to this patient (checked via Spring Security context)
- Map to `PredictionResponse`

#### `ListPredictionsUseCase`
- Query `PredictionRepository.findByPatientIdAndTimeRange` with pagination
- Map to `PagedPredictionResponse`

#### `MarkStaleTwinsUseCase`
- Query `DigitalTwinRepository.findStaleAfter(stalenessDuration)`
- For each: call `state.markStale()`, persist, log `TWIN_STATE_STALE`

### Unit tests to write (using `MockPredictionModel` from TASK-003)
- `TriggerPredictionUseCaseTest`:
  - Happy path: mock returns 0.75 → record COMPLETED, riskCategory HIGH
  - ML failure: mock throws → record FAILED, `PredictionServiceUnavailableException` raised
  - No glucose: `GlucoseReadingRequiredException` thrown before ML call
  - Stale twin: prediction runs, `STALE_WEARABLE_DATA` in warnings
  - Archived twin: `TwinStateArchivedError` thrown
- `IngestWearableEventUseCaseTest`:
  - Valid event: twin version incremented, event published
  - Out-of-range glucose: `ValidationException` thrown
  - Future timestamp: `ValidationException` thrown
  - Patient not found: `ResourceNotFoundException`
- `MarkStaleTwinsUseCaseTest`:
  - Two twins: one stale, one active → only stale one updated

### Acceptance criteria
- All use case unit tests pass
- `TriggerPredictionUseCase` never persists a COMPLETED record when the ML call fails
- `TriggerPredictionUseCase` snapshot is taken inside a `@Transactional(readOnly=true)` method

---

## TASK-009 — Security Layer

**Depends on:** TASK-004 (schema for patient access), TASK-008 (use cases to protect)  
**Satisfies:** REQ-022, security.md, design §12

### What to build

**Package:** `com.glucotwin.infrastructure.config` and `com.glucotwin.api`

- `SecurityConfig (@Configuration, @EnableWebSecurity)`:
  - JWT filter chain: all `/api/v1/**` endpoints require authentication
  - `/api/health`, `/api/docs/**` are public
  - `/api/admin/**` requires `ROLE_ADMIN`
  - CORS: allowed origins from `${ALLOWED_ORIGINS}` env var; `*` forbidden
  - CSRF disabled (JWT bearer auth, not cookie-based)
  - `SessionCreationPolicy.STATELESS`

- `JwtAuthenticationFilter`:
  - Extracts JWT from `Authorization: Bearer <token>` header
  - Validates RS256 signature against public key loaded from `${JWT_PUBLIC_KEY}` env var
  - Extracts `sub` (patientId scoping), `roles` claims
  - On invalid/expired token: returns `401` with structured error body

- `PatientAccessEvaluator` (`@Component`):
  - `isAssigned(patientId, authentication) → boolean`
  - Queries a `patient_clinician_assignments` table (stubbed for now — always returns `true` for ROLE_CLINICIAN in prototype; returns `true` always for ROLE_ADMIN)
  - Annotated with `@PreAuthorize` on use case methods

- `PatientIdRef` utility: `String hash(UUID patientId)` → `SHA-256(patientId.toString())` — used in all log statements

### Acceptance criteria
- Request to `/api/v1/patients/{id}/predictions` without JWT returns `401`
- Request with valid JWT but wrong role returns `403`
- Request with expired JWT returns `401`
- ROLE_ADMIN accesses any patient
- Integration test confirms each case using `@SpringBootTest` with test JWTs

---

## TASK-010 — REST Controllers and OpenAPI

**Depends on:** TASK-008, TASK-009  
**Satisfies:** REQ-020, REQ-021, design §6

### What to build

**Package:** `com.glucotwin.api`

Implement all six controllers (design §6.4). Each controller:
- Is annotated with `@RestController`, `@RequestMapping`, `@Validated`
- Delegates immediately to the corresponding use case
- Has no business logic
- Uses `@PreAuthorize` for role/patient-access checks

**DTOs** (`com.glucotwin.api.dto`):
- `WearableEventRequest` — with Jakarta Bean Validation annotations matching REQ-010 ranges
- `EhrUploadRequest` — with validation annotations matching REQ-009 ranges
- `PredictionResponse` — exact JSON shape from design §6.2
- `DigitalTwinStateResponse`
- `PagedPredictionResponse` — wraps `Page<PredictionResponse>`
- `ErrorResponse` — design §6.3 shape

**`GlobalExceptionHandler` (`@RestControllerAdvice`)**:
- Maps every exception in the hierarchy (design §11.1) to correct HTTP status and error code
- Injects `traceId` from MDC into every error response
- Logs full stack trace at ERROR server-side; never in response body

**OpenAPI configuration**:
- `SpringDocConfig (@Configuration)` — sets API title, version, description, security scheme (Bearer JWT)
- All endpoints documented with `@Operation`, `@ApiResponse`, `@Parameter` annotations
- API description explicitly states: "spikeProbability is a probabilistic estimate, not a certainty. This system is for research and decision support only."

**Request/Response mappers**:
- `WearableEventRequestMapper` — `WearableEventRequest → WearableEvent` domain object
- `PredictionResponseMapper` — `PredictionRecord → PredictionResponse` DTO

### Acceptance criteria
- `GET /api/docs` returns valid OpenAPI 3.1 JSON
- Controller slice tests (`@WebMvcTest`) cover all endpoints:
  - Happy path with valid request → correct status code
  - Validation failure → `400` with `ValidationException` error code
  - Missing JWT → `401`
  - Wrong patient → `403`
  - Resource not found → `404`
- `GlobalExceptionHandler` test: each exception type maps to the correct HTTP status

---

## TASK-011 — Background Jobs

**Depends on:** TASK-008  
**Satisfies:** REQ-008, design §3.3

### What to build

**Package:** `com.glucotwin.infrastructure.scheduling`

- `StaleTwinDetectionJob`:
  - Annotated with `@Scheduled(fixedDelayString = "${glucotwin.twin.stale-check-interval-ms:300000}")`
  - Calls `MarkStaleTwinsUseCase.execute()`
  - Logs count of twins marked stale per run
  - Staleness threshold configurable: `${glucotwin.twin.staleness-threshold-minutes:30}`

- `SchedulingConfig (@Configuration)`:
  - `@EnableScheduling`
  - Both the check interval and the staleness threshold are read from application config (not hardcoded)

### Acceptance criteria
- Integration test: insert a twin with `wearable_event_at` > 31 minutes ago; run the job; assert twin status = STALE
- Insert a twin with `wearable_event_at` < 29 minutes ago; run the job; assert twin status unchanged
- Log entry `TWIN_STATE_STALE` emitted for each stale twin found

---

## TASK-012 — Backend Integration Tests

**Depends on:** TASK-010, TASK-011  
**Satisfies:** REQ-029, REQ-030, testing.md §2 and §4

### What to build

**Package:** `com.glucotwin.integration` (test source set)

All tests use Testcontainers (`PostgreSQL 16`, `Redis 7`) and `MockPredictionModel`.

#### `FullPredictionFlowIT`
- POST wearable event → assert `202 Accepted`
- Assert `DigitalTwinState` version incremented in DB
- Assert `PredictionRecord` with status COMPLETED exists within 3 seconds
- Assert all required prediction response fields are present and valid
- Assert `twinStateVersion` in prediction matches the twin state version at time of prediction

#### `EhrIngestionIT`
- POST EHR → assert `201 Created`
- GET EHR → assert all fields returned correctly
- POST EHR with invalid BMI → assert `400` with correct error code

#### `PredictionFailureIT`
- Configure `MockPredictionModel` to throw `PredictionServiceUnavailableException`
- POST wearable event
- Assert `PredictionRecord` with status=FAILED persisted
- Assert no COMPLETED record created
- Assert API returns `502` for the prediction request

#### `ConcurrentTwinUpdateIT`
- 10 threads each apply a wearable event to the same patient
- Assert no lost updates (final `twinVersion` = initial + 10)
- Assert no duplicate prediction records created

#### `StaleTwinIT`
- Insert twin with old `wearable_event_at`
- Run `MarkStaleTwinsUseCase`
- Assert status = STALE
- Trigger prediction → assert `STALE_WEARABLE_DATA` in `dataQualityWarnings`

#### `DeadLetterIT`
- Configure Redis consumer to always throw on processing
- Publish wearable event
- Assert event appears in `dead_letter_events` after 3 retries

### Acceptance criteria
- All integration tests pass in CI using Testcontainers
- `FullPredictionFlowIT` completes end-to-end within 10 seconds (test timeout)
- All tests tagged `@Tag("integration")` and run as a separate Gradle test set

---

## TASK-013 — Feature Engineering (Python)

**Depends on:** TASK-007  
**Satisfies:** REQ-011, REQ-017, design §4

### What to build

**Location:** `ml-service/app/features/feature_engineering.py`

Implement `FeatureEngineer` class exactly as designed in §4.1:

- `extract(snapshot: TwinStateSnapshotDTO) -> FeatureVectorDTO`
- All 14 features as listed in REQ-017 and design §4.1
- `_impute(value, config_key)` — substitutes population median from `imputation_config.json`; adds field name to `imputed_fields`
- `_require_glucose(snapshot)` — raises `GlucoseReadingRequiredError` if absent
- `_cgm_delta(snapshot, minutes)` — computes from `cgmHistory` buffer; returns `None` + imputes if < 2 readings
- `_cgm_rolling_mean(snapshot, minutes)` — mean of readings within window
- `_cgm_linear_slope(snapshot, minutes)` — `numpy.polyfit` degree-1 slope; returns `None` if < 3 readings
- `_encode_activity(level)` — `SEDENTARY=0, LIGHT=1, MODERATE=2, VIGOROUS=3`; returns `1` (population median) if None
- `_days_since(onset_date)` — `(today - onset_date).days`; returns `0` if None
- Cyclical time encoding: `sin(2π * hour / 24)`, `cos(2π * hour / 24)`

Load `imputation_config.json` from the model artefact directory at startup.

### Unit tests to write (`tests/unit/test_feature_engineering.py`)
- All 14 features computed correctly for a fully-populated snapshot
- Missing HRV → HRV imputed, `"hrv_current"` in `imputed_fields`
- Missing glucose → `GlucoseReadingRequiredError` raised
- Empty CGM history → delta and slope imputed
- Exactly 2 CGM readings → delta computed, slope imputed
- Activity level `None` → encoded as `1`
- `_cgm_linear_slope` produces correct value for 3 known (time, glucose) pairs
- Time 06:00 → correct sin/cos values

### Property tests to write (`tests/property/test_feature_properties.py`)
- For any valid `TwinStateSnapshotDTO`, `extract()` never produces `NaN` or `Infinity`
- `extract()` is a pure function (same input → same output)
- `imputed_fields` is always a subset of known feature names

### Acceptance criteria
- All unit and property tests pass with `pytest tests/`
- `mypy --strict app/features/feature_engineering.py` passes with zero errors
- Feature names in `imputation_config.json` exactly match the names in `FeatureVectorDTO`

---

## TASK-014 — XGBoost Model, SHAP Explainer, and Confidence Interval

**Depends on:** TASK-013  
**Satisfies:** REQ-016, REQ-018, REQ-019, design §5

### What to build

**Location:** `ml-service/app/models/`

- `prediction_model_port.py` — abstract `PredictionModelPort` (design §5.1)

- `xgboost_model.py` — `XGBoostPredictionModel implements PredictionModelPort` (design §5.2):
  - Load `model.joblib`, `explainer.joblib`, `calibration_scores.npy`, `metadata.json` from `model_path`
  - `predict(features: FeatureVectorDTO) → PredictionResultDTO`:
    - Convert `FeatureVectorDTO` to numpy array (fixed column order — order defined in `metadata.json`)
    - `predict_proba` → extract class-1 probability
    - Clamp to [0.0, 1.0]
    - Conformal prediction CI: compute nonconformity score, look up calibration quantiles for 95% coverage
    - SHAP: `explainer.shap_values(X)[0]` → top-5 contributors by absolute value with sign direction
    - Return `PredictionResultDTO`

- `model_loader.py` — `load_model(model_dir: Path) → PredictionModelPort`:
  - Reads `metadata.json` to determine model type
  - Returns appropriate implementation
  - Raises `ModelLoadError` with descriptive message if artefact missing or corrupt

- **Synthetic model training script** (`scripts/train_synthetic_model.py`):
  - Generates synthetic glucose spike training data (1000 patients, 100 events each)
  - Trains `XGBClassifier` with `use_label_encoder=False`, `eval_metric='logloss'`
  - Trains SHAP `TreeExplainer` on the training set
  - Computes calibration scores on a held-out 20% split
  - Saves all artefacts to `ml-service/models/xgboost-v1.0.0/`
  - Produces `metadata.json` with `{ "version": "xgboost-v1.0.0", "trainedAt": "...", "featureOrder": [...], "glucoseSpikeThreshold": 10.0 }`

- **Mock model** (`tests/mocks/mock_prediction_model.py`):
  - `MockPredictionModel(fixed_probability=0.5, raise_on_predict=None)`
  - Implements `PredictionModelPort`

### Unit tests to write (`tests/unit/test_xgboost_model.py`)
- `predict()` output `spike_probability` is always in [0.0, 1.0]
- `confidence_interval.low ≤ spike_probability ≤ confidence_interval.high`
- `confidence_interval` width > 0
- `top_contributing_factors` has ≤ 5 entries and all `contribution` values are floats
- `model_version` matches the version in `metadata.json`
- `predict()` on a synthetic high-risk snapshot returns probability > 0.5 (smoke test)

### Property tests to write (`tests/property/test_model_properties.py`)
- For any valid `FeatureVectorDTO`, `spike_probability` is in [0.0, 1.0]
- CI bounds are in [0.0, 1.0] and `low ≤ high`

### Acceptance criteria
- `train_synthetic_model.py` runs without error and produces all artefacts
- All unit and property tests pass
- `mypy --strict app/models/` passes with zero errors
- Model validation script (`scripts/validate_model.py`) passes all thresholds: AUC-ROC ≥ 0.80, Precision ≥ 0.70, Recall ≥ 0.65, ECE ≤ 0.10

---

## TASK-015 — ML Service FastAPI Endpoints

**Depends on:** TASK-014  
**Satisfies:** design §9

### What to build

**Location:** `ml-service/app/api/`

- `prediction_router.py`:
  - `POST /predict`:
    - Accepts `TwinStateSnapshotDTO`
    - Calls `FeatureEngineer.extract()`
    - Calls `PredictionModelPort.predict()`
    - Returns `PredictionResponseDTO`
    - On `GlucoseReadingRequiredError` → `422` with `GLUCOSE_READING_REQUIRED`
    - On `ModelLoadError` → `503` with `MODEL_NOT_READY`
  - `GET /health` → `{ "status": "ok", "modelVersion": "..." }`
  - `GET /model/info` → full metadata

- `InternalTokenMiddleware`:
  - Checks `X-Internal-Token` header against `${ML_SERVICE_INTERNAL_TOKEN}` env var
  - On mismatch: returns `401`
  - Exempt paths: `/health`

- Exception handlers registered in `main.py`:
  - `GlucoseReadingRequiredError` → `422`
  - `ValidationError` (Pydantic) → `422`
  - `ModelLoadError` → `503`
  - Unhandled exceptions → `500` with generic message (no stack trace in response)

### Integration tests to write (`tests/integration/test_prediction_api.py`)
- POST `/predict` with valid full snapshot → `200`, response matches `PredictionResponseDTO` schema
- POST `/predict` with missing glucose → `422`, error code `GLUCOSE_READING_REQUIRED`
- POST `/predict` without `X-Internal-Token` → `401`
- POST `/predict` with missing optional fields → `200`, `imputed_fields` non-empty in response
- GET `/health` → `200`
- GET `/health` without token → `200` (exempt)

### Acceptance criteria
- All integration tests pass with `pytest tests/integration/`
- All endpoints documented in FastAPI auto-generated OpenAPI (visible at `/docs` in development)
- `mypy --strict app/api/` passes with zero errors

---

## TASK-016 — ML Service Unit and Property Test Suite

**Depends on:** TASK-013, TASK-014, TASK-015  
**Satisfies:** testing.md §1, §5

### What to build

Consolidate and complete the ML test suite:

- Run full test suite: `pytest tests/ --cov=app --cov-report=term-missing`
- Ensure `app/models/` line coverage ≥ 80%
- Ensure `app/features/` line coverage ≥ 80%
- Add `pytest.ini` or `pyproject.toml` test configuration:
  - `markers`: `unit`, `integration`, `property`, `slow`
  - `asyncio_mode = "auto"`
  - `filterwarnings` to suppress expected deprecation warnings

- Final property test: end-to-end `FeatureEngineer → XGBoostPredictionModel` pipeline:
  - For any valid synthetic snapshot, the full pipeline produces a valid `PredictionResponseDTO`
  - No NaN, no Infinity, probability in [0.0, 1.0], CI valid, factors ≤ 5

### Acceptance criteria
- `pytest tests/ -m "not slow"` completes in < 30 seconds
- `app/models/` and `app/features/` coverage ≥ 80%
- `mypy --strict app/` passes with zero errors across the entire `app/` package

---

## TASK-017 — Observability: Logging, Metrics, Tracing

**Depends on:** TASK-008, TASK-015  
**Satisfies:** REQ-032, REQ-033, REQ-034, design §14

### What to build

#### Java Backend

- `LoggingConfig`:
  - Logback JSON encoder for non-local profiles
  - MDC keys: `traceId`, `spanId`, `patientIdRef`, `service`
  - `PatientIdRef.hash(UUID)` utility — SHA-256
  - `LogEvent` constants enum with all event names from design §14.1

- Add structured log calls to all use cases using `LogEvent` constants and `PatientIdRef`:
  - `WEARABLE_EVENT_RECEIVED` in `IngestWearableEventUseCase`
  - `TWIN_STATE_UPDATED` in `IngestWearableEventUseCase`
  - `PREDICTION_TRIGGERED` in `TriggerPredictionUseCase`
  - `PREDICTION_COMPLETED` in `TriggerPredictionUseCase`
  - `PREDICTION_FAILED` in `TriggerPredictionUseCase`
  - `TWIN_STATE_STALE` in `MarkStaleTwinsUseCase`
  - `DEAD_LETTER_EVENT` in `WearableEventConsumer`

- OpenTelemetry metrics (Micrometer + OTLP exporter):
  - Register all metrics from design §14.2
  - `prediction_latency_seconds` histogram: measure from event receipt to prediction completion
  - Tag all metrics with `service=glucotwin-backend`

- W3C `traceparent` propagation:
  - Extract from incoming HTTP request
  - Inject into Redis stream message headers
  - Inject into ML service HTTP call headers

#### Python ML Service

- JSON logging with `structlog` or standard `logging` + `python-json-logger`
- Log `PREDICTION_COMPLETED` and `PREDICTION_FAILED` events with `trace_id`, `model_version`, `latency_ms`
- Extract `traceparent` from `X-B3-TraceId` / `traceparent` headers; include in all log entries

### Acceptance criteria
- All 7 log events from design §14.1 are emitted for a full prediction flow
- No raw `patientId` UUID appears in any log (only `patientIdRef` hash)
- `prediction_latency_seconds` metric is present in Actuator `/actuator/metrics` endpoint
- TraceId from wearable event request is present in the corresponding prediction log entry

---

## TASK-018 — Safety Constraint Tests

**Depends on:** TASK-010, TASK-015  
**Satisfies:** REQ-025, REQ-026, REQ-027, REQ-028, REQ-037, testing.md §7

### What to build

**Java:** `com.glucotwin.safety.PredictionSafetyConstraintTest` (tagged `@Tag("safety")`)

```
Test: noForbiddenDiagnosticTermsInPredictionResponse
  - Trigger a prediction via full stack (using MockPredictionModel)
  - GET the prediction response
  - Assert response JSON contains NONE of the forbidden terms:
    diagnose, diagnosis, you have, confirms, prescribe, prescription,
    take [dose], guaranteed, certain
  - Test fails (and fails CI) if any term found

Test: uncertaintyFieldsAlwaysPresent
  - GET prediction response
  - Assert spikeProbability is non-null and in [0.0, 1.0]
  - Assert riskCategory is non-null and is one of LOW|MODERATE|HIGH|CRITICAL
  - Assert confidenceInterval is non-null with both low and high present
  - Assert confidenceInterval.low ≤ spikeProbability ≤ confidenceInterval.high

Test: dataProvenanceAlwaysPredicted
  - GET prediction response
  - Assert dataProvenance == "PREDICTED"
  - Never "OBSERVED" or "SIMULATED" for model output

Test: predictionHorizonAlwaysTwo
  - GET prediction response
  - Assert predictionHorizonHours == 2

Test: syntheticSeedDataOnly
  - Query database directly
  - Assert all records in patient_ehr, wearable_events_raw have data_source IN ('SYNTHETIC', 'ANONYMISED')
  - Assert no record has data_source = NULL
```

**Python:** `tests/safety/test_prediction_safety.py`
- Mirror the forbidden-terms check against the raw ML service response before it reaches the Java backend
- Assert `imputed_fields` names are all valid known feature names (no injection)

### Acceptance criteria
- All safety tests pass in CI
- Safety tests are run as a separate step in the CI pipeline: `./gradlew test --tests "*.safety.*"`
- A deliberate injection of a forbidden term into `MockPredictionModel` causes the safety test to fail (meta-test)
- CI pipeline fails immediately if any `@Tag("safety")` test fails

---

## TASK-019 — Performance and Load Tests

**Depends on:** TASK-012, TASK-017  
**Satisfies:** REQ-029, REQ-030, REQ-031

### What to build

**Location:** `scripts/load-test/`

- `locustfile.py` (Locust framework):
  - `WearableEventUser`: 100 simulated patients, each posting 1 wearable event per minute
  - Measures end-to-end latency: time from POST response until `GET /predictions/{id}` returns status=COMPLETED
  - Reports p50, p95, p99 latency
  - Fails if p95 > 3 seconds (REQ-029)

- `on_demand_prediction_test.py` (separate Locust task):
  - 50 concurrent clinicians each triggering `POST /patients/{id}/predictions`
  - Polls until COMPLETED, measures total time
  - Fails if p95 > 3 seconds (REQ-030)

- `README.md` in `scripts/load-test/` documenting how to run locally with `docker compose --profile test`

### Acceptance criteria
- Load test with 100 patients at 1 event/min achieves p95 ≤ 3 seconds
- Load test with 50 concurrent on-demand predictions achieves p95 ≤ 3 seconds
- No prediction records lost or duplicated during load test (verified by count assertion)
- Load test results are captured as a CI artefact (JSON report)

---

## TASK-020 — Docker Compose Local Development Stack

**Depends on:** TASK-001, TASK-004, TASK-007  
**Satisfies:** tech.md (local-first dev), structure.md (top-level layout)

### What to build

**Root-level files:** `docker-compose.yml`, `docker-compose.override.yml`

#### `docker-compose.yml` (production-like config)
Services:
- `postgres`: `postgres:16-alpine`, port `5432`, volume `glucotwin-db-data`, env from `.env`
- `redis`: `redis:7-alpine`, port `6379`, volume `glucotwin-redis-data`
- `backend`: built from `backend/Dockerfile`, port `8080`, depends on postgres + redis
- `ml-service`: built from `ml-service/Dockerfile`, port `8000`, depends on postgres

#### `docker-compose.override.yml` (local dev overrides)
- Backend hot-reload via Spring DevTools
- ML service hot-reload via `uvicorn --reload`
- Bind mount source directories for live code changes
- Expose additional debug ports

#### `backend/Dockerfile`
- Multi-stage: `gradle build` in builder stage → copy JAR to runtime stage
- Runtime: `eclipse-temurin:21-jre-alpine`
- Non-root user
- Health check: `curl /api/health`

#### `scripts/local-setup.sh` (PowerShell: `scripts/local-setup.ps1`)
- Copies `.env.example` to `.env` (if `.env` doesn't exist)
- Pulls Docker images
- Runs Flyway migrations
- Seeds synthetic data

#### `scripts/seed-synthetic-data.sql`
- Idempotent seed script (uses `INSERT ... ON CONFLICT DO NOTHING`)
- Inserts 5 synthetic patients with EHR + 20 wearable events each
- All records have `data_source = 'SYNTHETIC'`

### Acceptance criteria
- `docker compose up` from the project root brings up all 4 services successfully
- `GET http://localhost:8080/api/health` returns `200`
- `GET http://localhost:8000/health` returns `200`
- Flyway migrations run on backend startup
- Synthetic seed data is loaded and queryable
- `docker compose down -v` cleanly removes all data volumes

---

## Task Summary Table

| Task | Name | Priority | Depends On | Satisfies (key REQs) |
|---|---|---|---|---|
| TASK-001 | Project Scaffold | Must | — | tech.md, structure.md |
| TASK-002 | Domain Model | Must | 001 | REQ-003, 005, 013, 017 |
| TASK-003 | Port Interfaces | Must | 002 | REQ-016 |
| TASK-004 | DB Schema + Flyway | Must | 003 | REQ-014 |
| TASK-005 | JPA Repositories | Must | 004 | REQ-014 |
| TASK-006 | Redis Stream | Must | 003 | REQ-002, 024 |
| TASK-007 | ML Service Scaffold | Must | 001 | tech.md, design §9 |
| TASK-008 | Application Use Cases | Must | 005, 006, 007 | REQ-001–008, 011, 015 |
| TASK-009 | Security Layer | Must | 004, 008 | REQ-022, security.md |
| TASK-010 | REST Controllers + OpenAPI | Must | 008, 009 | REQ-020, 021 |
| TASK-011 | Background Jobs | Must | 008 | REQ-008 |
| TASK-012 | Backend Integration Tests | Must | 010, 011 | REQ-029, 030 |
| TASK-013 | Feature Engineering (Python) | Must | 007 | REQ-011, 017 |
| TASK-014 | XGBoost + SHAP + CI | Must | 013 | REQ-016, 018, 019 |
| TASK-015 | ML FastAPI Endpoints | Must | 014 | design §9 |
| TASK-016 | ML Test Suite | Must | 013, 014, 015 | testing.md §1, §5 |
| TASK-017 | Observability | Must | 008, 015 | REQ-032, 033, 034 |
| TASK-018 | Safety Constraint Tests | Must | 010, 015 | REQ-025–028, 037 |
| TASK-019 | Load Tests | Should | 012, 017 | REQ-029, 030, 031 |
| TASK-020 | Docker Compose Stack | Must | 001, 004, 007 | tech.md (local-first dev) |
