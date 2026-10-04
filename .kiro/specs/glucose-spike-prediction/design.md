# Design: Two-Hour Glucose Spike Prediction

## Overview

This document translates the requirements in `requirements.md` into a concrete technical design. It covers the domain model, service architecture, data flows, persistence schema, API contract, error handling, security, testing strategy, and extension points for future capabilities (LangGraph agents, MCP tools, RAG, what-if simulation).

All design decisions conform to `tech.md`, `architecture.md`, `structure.md`, `coding-standards.md`, and `security.md`.

---

## 1. Domain Model

### 1.1 Core Aggregates and Entities

```
┌──────────────────────────────────────────────────────────────────┐
│  DOMAIN LAYER  (com.glucotwin.domain)                            │
│                                                                  │
│  ┌──────────────┐     ┌──────────────────────┐                  │
│  │   Patient    │1───1│  DigitalTwinState     │                  │
│  │  (Entity)    │     │  (Aggregate Root)     │                  │
│  └──────────────┘     └──────────────────────┘                  │
│         │                         │                             │
│         │             ┌───────────┴──────────┐                  │
│         │             │                      │                  │
│  ┌──────▼──────┐  ┌───▼──────────┐  ┌────────▼──────┐          │
│  │  EhrRecord  │  │ StaticLayer  │  │ DynamicLayer  │          │
│  │  (Entity)   │  │ (Value Obj.) │  │ (Value Obj.)  │          │
│  └─────────────┘  └──────────────┘  └───────────────┘          │
│                                                                  │
│  ┌──────────────────────┐    ┌───────────────────────┐          │
│  │  WearableEvent       │    │  PredictionRecord      │          │
│  │  (Domain Event)      │    │  (Entity)              │          │
│  └──────────────────────┘    └───────────────────────┘          │
│                                                                  │
│  ┌──────────────────────┐    ┌───────────────────────┐          │
│  │  TwinStateSnapshot   │    │  FeatureVector         │          │
│  │  (Value Object)      │    │  (Value Object)        │          │
│  └──────────────────────┘    └───────────────────────┘          │
└──────────────────────────────────────────────────────────────────┘
```

### 1.2 DigitalTwinState (Aggregate Root)

```java
// com.glucotwin.domain.twin.DigitalTwinState
public class DigitalTwinState {
    private final PatientId patientId;
    private TwinStatus status;           // INITIALISED | ACTIVE | STALE | ARCHIVED
    private int twinVersion;             // monotonically increasing
    private StaticLayer staticLayer;
    private DynamicLayer dynamicLayer;
    private Instant lastUpdatedAt;
    private Instant createdAt;

    // Domain behaviour
    public DigitalTwinState applyEhrRecord(EhrRecord ehr) { ... }
    public DigitalTwinState applyWearableEvent(WearableEvent event) { ... }
    public TwinStateSnapshot snapshot() { ... }  // immutable copy for prediction
    public void markStale() { ... }
    public void archive() { ... }
}
```

**Invariants enforced by the aggregate:**
- `twinVersion` increments on every `apply*` call; never decremented.
- `status` transitions are unidirectional: `INITIALISED → ACTIVE → STALE ↔ ACTIVE → ARCHIVED`. Cannot un-archive.
- `lastUpdatedAt` is always ≤ current system time.

### 1.3 StaticLayer (Value Object)

```java
public record StaticLayer(
    String firstName,               // display only, not logged in clear
    String lastName,                // display only, not logged in clear
    LocalDate dateOfBirth,
    Sex sex,                        // MALE | FEMALE | OTHER
    Double bmi,                     // kg/m², nullable
    LocalDate diabetesOnsetDate,
    List<Medication> medications,
    List<LabResult> labResults,
    DataProvenance dataProvenance   // always OBSERVED
) {}
```

### 1.4 DynamicLayer (Value Object)

```java
public record DynamicLayer(
    Double glucoseReading,          // mmol/L, required for prediction
    Double heartRate,               // bpm, nullable
    Double hrv,                     // ms, nullable
    Double sleepDuration,           // hours, nullable
    SleepStage sleepStage,          // AWAKE|LIGHT|DEEP|REM, nullable
    Integer stepCount,              // daily total, nullable
    ActivityLevel activityLevel,    // SEDENTARY|LIGHT|MODERATE|VIGOROUS, nullable
    Instant eventTimestamp,
    Set<String> dataQualityWarnings,
    DataProvenance dataProvenance   // always OBSERVED
) {}
```

### 1.5 TwinStateSnapshot (Value Object — immutable)

```java
public record TwinStateSnapshot(
    PatientId patientId,
    int twinVersion,
    TwinStatus status,
    StaticLayer staticLayer,
    DynamicLayer dynamicLayer,
    Instant snapshotTakenAt
) {
    // Factory — called inside a read transaction
    public static TwinStateSnapshot from(DigitalTwinState state) { ... }
}
```

### 1.6 PredictionRecord (Entity)

```java
public class PredictionRecord {
    private final PredictionId predictionId;
    private final PatientId patientId;
    private PredictionStatus status;    // PENDING | COMPLETED | FAILED
    private Instant predictedAt;
    private int predictionHorizonHours; // fixed: 2
    private Double spikeProbability;    // [0.0, 1.0]
    private RiskCategory riskCategory;  // LOW|MODERATE|HIGH|CRITICAL
    private ConfidenceInterval confidenceInterval;
    private List<ContributingFactor> topContributingFactors;
    private DataProvenance dataProvenance; // always PREDICTED
    private int twinStateVersion;
    private String modelVersion;
    private List<String> dataQualityWarnings;
    private String failureReason;       // null when status=COMPLETED
    private FeatureVector featureVector; // stored for audit/debugging
}
```

### 1.7 FeatureVector (Value Object)

```java
public record FeatureVector(
    double cgmCurrent,
    Double cgmDelta30m,         // null if insufficient history
    Double cgmMean60m,
    Double cgmSlope60m,
    double heartRateCurrent,
    Double hrvCurrent,
    Double sleepDurationLast,
    int stepCountToday,
    int activityLevelEncoded,   // 0=SEDENTARY, 1=LIGHT, 2=MODERATE, 3=VIGOROUS
    Double hba1cLatest,
    Double bmi,
    double timeOfDaySin,
    double timeOfDayCos,
    long daysSinceDiagnosis,
    Set<String> imputedFields   // names of fields that were imputed
) {}
```

### 1.8 Domain Enumerations

```java
enum TwinStatus        { INITIALISED, ACTIVE, STALE, ARCHIVED }
enum RiskCategory      { LOW, MODERATE, HIGH, CRITICAL }
enum DataProvenance    { OBSERVED, PREDICTED, SIMULATED }
enum SleepStage        { AWAKE, LIGHT, DEEP, REM }
enum ActivityLevel     { SEDENTARY, LIGHT, MODERATE, VIGOROUS }
enum PredictionStatus  { PENDING, COMPLETED, FAILED }
enum Sex               { MALE, FEMALE, OTHER }
```

---

## 2. Port Interfaces (Domain ↔ Infrastructure Boundary)

Following hexagonal architecture — domain defines ports, infrastructure implements them.

```java
// com.glucotwin.domain.twin.DigitalTwinRepository
public interface DigitalTwinRepository {
    Optional<DigitalTwinState> findByPatientId(PatientId id);
    DigitalTwinState save(DigitalTwinState state);            // optimistic lock
    List<DigitalTwinState> findStaleAfter(Duration threshold);
}

// com.glucotwin.domain.prediction.PredictionRepository
public interface PredictionRepository {
    PredictionRecord save(PredictionRecord record);
    Optional<PredictionRecord> findById(PredictionId id);
    Page<PredictionRecord> findByPatientId(PatientId id, Pageable pageable);
    Page<PredictionRecord> findByPatientIdAndTimeRange(
        PatientId id, Instant from, Instant to, Pageable pageable);
    Page<PredictionRecord> findByModelVersion(String modelVersion, Pageable pageable);
}

// com.glucotwin.domain.prediction.PredictionModelPort  [REQ-016]
public interface PredictionModelPort {
    PredictionResult predict(TwinStateSnapshot snapshot, FeatureVector features);
    String getModelVersion();
}

// com.glucotwin.domain.wearable.WearableEventPublisher
public interface WearableEventPublisher {
    void publish(WearableEvent event);
}

// com.glucotwin.domain.twin.TwinStateEventPublisher
public interface TwinStateEventPublisher {
    void publishUpdated(TwinStateSnapshot snapshot);
}
```

### 2.1 PredictionResult (Port Return Type)

```java
public record PredictionResult(
    double spikeProbability,
    ConfidenceInterval confidenceInterval,
    List<ContributingFactor> topContributingFactors,
    String modelVersion
) {}
```

---

## 3. Application Services (Use Case Layer)

```
com.glucotwin.application/
├── IngestEhrUseCase
├── IngestWearableEventUseCase
├── GetDigitalTwinStateUseCase
├── TriggerPredictionUseCase
├── GetPredictionUseCase
├── ListPredictionsUseCase
└── MarkStaleTwinsUseCase          (scheduled, called by background job)
```

### 3.1 IngestWearableEventUseCase

```
Input:  IngestWearableEventCommand { patientId, wearableEventPayload }
Output: void (async — event published to Redis)

Steps:
  1. Load DigitalTwinState for patientId (404 if patient unknown)
  2. Validate WearableEvent fields [REQ-010]
  3. Apply event to DigitalTwinState → new version
  4. Persist updated DigitalTwinState [REQ-014]
  5. Publish WearableEvent to Redis stream [REQ-002]
  6. Return 202 Accepted
```

### 3.2 TriggerPredictionUseCase

```
Input:  TriggerPredictionCommand { patientId, triggeredBy: AUTO|MANUAL }
Output: PredictionId

Steps:
  1. Load DigitalTwinState; assert status ≠ ARCHIVED
  2. Assert glucoseReading is present [REQ-011]
  3. Take TwinStateSnapshot (inside read transaction) [REQ-015]
  4. Create PredictionRecord with status=PENDING; persist [REQ-023]
  5. Build FeatureVector from snapshot [REQ-017]
  6. Call PredictionModelPort.predict(snapshot, features)
     → on success: update record to COMPLETED, set all output fields
     → on failure: update record to FAILED, set failureReason [REQ-023]
  7. Derive RiskCategory from spikeProbability + config thresholds [REQ-006]
  8. Persist final PredictionRecord
  9. Publish TwinStateUpdated event (triggers SSE push to dashboard)
  10. Return PredictionId
```

### 3.3 MarkStaleTwinsUseCase

```
Triggered: scheduled every 5 minutes [REQ-008]

Steps:
  1. Query DigitalTwinRepository.findStaleAfter(staleness threshold)
  2. For each result: call state.markStale()
  3. Persist updated states
  4. Log TWIN_STATE_STALE event for each [REQ-032]
```

---

## 4. Feature Engineering

Feature engineering runs in the **ML service** (Python), not in the Java backend. The backend passes the `TwinStateSnapshot` as a JSON payload to the ML service; the ML service performs all feature computation.

### 4.1 Feature Engineering Pipeline (Python — `ml-service/app/features/`)

```python
# feature_engineering.py

class FeatureEngineer:
    def extract(self, snapshot: TwinStateSnapshotDTO) -> FeatureVector:
        """
        Pure function. Same input always produces same output.
        Raises FeatureExtractionError if glucose reading is absent.
        """
        features = {}

        # Primary glucose signal — required
        features["cgm_current"] = self._require_glucose(snapshot)

        # Temporal glucose features (require CGM history from dynamic layer)
        features["cgm_delta_30m"]  = self._cgm_delta(snapshot, minutes=30)
        features["cgm_mean_60m"]   = self._cgm_rolling_mean(snapshot, minutes=60)
        features["cgm_slope_60m"]  = self._cgm_linear_slope(snapshot, minutes=60)

        # Wearable signals — imputed if missing
        features["heart_rate_current"] = self._impute(snapshot.heartRate, "heart_rate_median")
        features["hrv_current"]         = self._impute(snapshot.hrv, "hrv_median")
        features["sleep_duration_last"] = self._impute(snapshot.sleepDuration, "sleep_median")
        features["step_count_today"]    = self._impute(snapshot.stepCount, "step_median")
        features["activity_level_enc"]  = self._encode_activity(snapshot.activityLevel)

        # Static EHR features
        features["hba1c_latest"]        = self._impute(snapshot.hba1c, "hba1c_median")
        features["bmi"]                 = self._impute(snapshot.bmi, "bmi_median")
        features["days_since_diagnosis"] = self._days_since(snapshot.diabetesOnsetDate)

        # Cyclical time encoding
        hour = snapshot.eventTimestamp.hour
        features["time_of_day_sin"] = math.sin(2 * math.pi * hour / 24)
        features["time_of_day_cos"] = math.cos(2 * math.pi * hour / 24)

        return FeatureVector(**features, imputed_fields=self._imputed)
```

**Imputation strategy** (REQ-011):
- Imputed values are population medians loaded from a config file at startup.
- Each imputed field is added to `FeatureVector.imputed_fields`.
- This set is propagated into `PredictionRecord.dataQualityWarnings` as `"IMPUTED_FIELD:<name>"`.

**CGM history** — the dynamic layer stores a short rolling buffer of the last N CGM readings with timestamps (configurable, default N=12 readings). This enables delta and slope computation. If fewer readings are available than needed, delta/slope are imputed and flagged.

---

## 5. ML Model Design

### 5.1 Model Abstraction (Python — `ml-service/app/models/`)

```python
# prediction_model_port.py
from abc import ABC, abstractmethod

class PredictionModelPort(ABC):
    @abstractmethod
    def predict(self, features: FeatureVector) -> PredictionResult:
        """Returns spike probability, CI, contributing factors, model version."""

    @abstractmethod
    def get_model_version(self) -> str: ...
```

### 5.2 XGBoostPredictionModel (v1 implementation)

```python
# xgboost_model.py
class XGBoostPredictionModel(PredictionModelPort):
    def __init__(self, model_path: Path, explainer_path: Path):
        self._model    = joblib.load(model_path)       # XGBClassifier
        self._explainer = joblib.load(explainer_path)  # shap.TreeExplainer
        self._version   = self._read_version(model_path)

    def predict(self, features: FeatureVector) -> PredictionResult:
        X = features.to_numpy_array()

        # Spike probability
        prob = float(self._model.predict_proba(X)[0, 1])
        prob = max(0.0, min(1.0, prob))  # clamp [REQ-005]

        # Confidence interval via bootstrap or Platt scaling
        ci = self._compute_confidence_interval(X)

        # SHAP top-5 contributors [REQ-017]
        shap_values = self._explainer.shap_values(X)[0]
        top_factors = self._top_n_factors(shap_values, features, n=5)

        return PredictionResult(
            spike_probability=prob,
            confidence_interval=ci,
            top_contributing_factors=top_factors,
            model_version=self._version,
        )
```

### 5.3 Confidence Interval Computation

The v1 approach uses **conformal prediction** (split conformal, calibrated on a held-out validation set). This produces valid coverage guarantees (≥ 95% of true outcomes fall within the interval) without requiring a full ensemble. The calibration set scores are stored alongside the model artefact.

Future: replace with full Bayesian or ensemble interval when model matures.

### 5.4 Model Artefact Storage

```
ml-service/models/
├── xgboost-v1.0.0/
│   ├── model.joblib          # trained XGBClassifier
│   ├── explainer.joblib      # SHAP TreeExplainer
│   ├── calibration_scores.npy # for conformal CI
│   ├── imputation_config.json # population medians
│   └── metadata.json         # { "version": "xgboost-v1.0.0", "trainedAt": "..." }
```

Production: artefacts stored in S3, downloaded on container startup. Version pinned in environment variable `MODEL_VERSION`.

---

## 6. REST API Design

### 6.1 Endpoint Specifications

#### POST `/api/v1/patients/{patientId}/ehr`
```
Auth:     ROLE_CLINICIAN | ROLE_ADMIN
Request:  EhrUploadRequest (JSON)
Response: 201 Created, body: EhrResponse
Errors:   400 (validation), 401, 403, 404 (patient not found)
```

#### POST `/api/v1/patients/{patientId}/wearable-events`
```
Auth:     ROLE_CLINICIAN | ROLE_ADMIN
Request:  WearableEventRequest (JSON)
Response: 202 Accepted, body: { "eventId": "uuid", "status": "QUEUED" }
Errors:   400 (validation), 401, 403, 404
```

#### GET `/api/v1/patients/{patientId}/twin-state`
```
Auth:     ROLE_CLINICIAN | ROLE_ADMIN
Response: 200 OK, body: DigitalTwinStateResponse
Errors:   401, 403, 404
```

#### POST `/api/v1/patients/{patientId}/predictions`
```
Auth:     ROLE_CLINICIAN | ROLE_ADMIN
Request:  {} (empty body — horizon is fixed at 2h in v1)
Response: 202 Accepted, body: { "predictionId": "uuid", "status": "PENDING" }
Errors:   400 (no glucose reading), 401, 403, 404, 422 (GLUCOSE_READING_REQUIRED)
```

#### GET `/api/v1/predictions/{predictionId}`
```
Auth:     ROLE_CLINICIAN | ROLE_ADMIN
Response: 200 OK, body: PredictionResponse (full)
Errors:   401, 403, 404
```

#### GET `/api/v1/patients/{patientId}/predictions`
```
Auth:     ROLE_CLINICIAN | ROLE_ADMIN
Params:   page (default 0), size (default 20, max 100),
          from (ISO8601), to (ISO8601), modelVersion
Response: 200 OK, body: PagedPredictionResponse
Errors:   400 (invalid params), 401, 403, 404
```

#### GET `/api/v1/admin/dead-letter-events`
```
Auth:     ROLE_ADMIN only
Response: 200 OK, body: PagedDeadLetterEventResponse
```

### 6.2 PredictionResponse Schema

```json
{
  "predictionId":           "uuid",
  "patientId":              "uuid",
  "predictedAt":            "2026-10-04T12:00:00Z",
  "predictionHorizonHours": 2,
  "status":                 "COMPLETED",
  "spikeProbability":       0.72,
  "riskCategory":           "HIGH",
  "confidenceInterval": {
    "low":  0.61,
    "high": 0.81
  },
  "topContributingFactors": [
    { "factorName": "cgm_current",    "contribution": 0.31, "direction": "INCREASES_RISK" },
    { "factorName": "cgm_slope_60m",  "contribution": 0.18, "direction": "INCREASES_RISK" },
    { "factorName": "step_count_today","contribution": 0.09, "direction": "DECREASES_RISK" },
    { "factorName": "hba1c_latest",   "contribution": 0.07, "direction": "INCREASES_RISK" },
    { "factorName": "hrv_current",    "contribution": 0.04, "direction": "DECREASES_RISK" }
  ],
  "dataProvenance":      "PREDICTED",
  "twinStateVersion":    14,
  "modelVersion":        "xgboost-v1.0.0",
  "dataQualityWarnings": ["IMPUTED_FIELD:hrv_current"],
  "failureReason":       null
}
```

### 6.3 Error Response Schema

```json
{
  "error":     "GLUCOSE_READING_REQUIRED",
  "message":   "Prediction cannot run: no glucose reading in current twin state.",
  "timestamp": "2026-10-04T12:00:00Z",
  "traceId":   "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

### 6.4 Controller Layer (Java)

```
com.glucotwin.api/
├── PatientEhrController        → IngestEhrUseCase
├── WearableEventController     → IngestWearableEventUseCase
├── DigitalTwinStateController  → GetDigitalTwinStateUseCase
├── PredictionController        → TriggerPredictionUseCase
│                                  GetPredictionUseCase
│                                  ListPredictionsUseCase
└── AdminController             → dead-letter query
```

All controllers are annotated with `@RestController`, `@Validated`, and delegate entirely to application use case classes. No business logic in controllers.

---

## 7. Persistence Design

### 7.1 Database Schema (PostgreSQL 16)

#### `patients`
```sql
CREATE TABLE patients (
    patient_id      UUID PRIMARY KEY,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

#### `patient_ehr`
```sql
CREATE TABLE patient_ehr (
    ehr_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id          UUID NOT NULL REFERENCES patients(patient_id),
    date_of_birth       DATE NOT NULL,
    sex                 VARCHAR(10) NOT NULL,
    bmi                 NUMERIC(5,2),
    diabetes_onset_date DATE NOT NULL,
    hba1c               NUMERIC(5,2),
    fasting_glucose     NUMERIC(5,2),
    medications         JSONB,          -- array of { name, dose, frequency }
    lab_results         JSONB,          -- array of { type, value, unit, recordedAt }
    data_source         VARCHAR(20) NOT NULL DEFAULT 'SYNTHETIC',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

#### `digital_twin_states`
```sql
CREATE TABLE digital_twin_states (
    patient_id          UUID PRIMARY KEY REFERENCES patients(patient_id),
    twin_version        INTEGER NOT NULL DEFAULT 1,
    status              VARCHAR(20) NOT NULL DEFAULT 'INITIALISED',
    -- Dynamic layer (latest wearable snapshot)
    glucose_reading     NUMERIC(5,2),
    heart_rate          NUMERIC(6,2),
    hrv                 NUMERIC(6,2),
    sleep_duration      NUMERIC(4,2),
    sleep_stage         VARCHAR(10),
    step_count          INTEGER,
    activity_level      VARCHAR(20),
    wearable_event_at   TIMESTAMPTZ,
    cgm_history         JSONB,          -- rolling buffer: [{value, timestamp}]
    data_quality_flags  TEXT[],
    -- Metadata
    last_updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Optimistic locking
    version             INTEGER NOT NULL DEFAULT 0  -- JPA @Version column
);
```

#### `glucose_predictions`
```sql
CREATE TABLE glucose_predictions (
    prediction_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id              UUID NOT NULL REFERENCES patients(patient_id),
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    predicted_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    prediction_horizon_hrs  INTEGER NOT NULL DEFAULT 2,
    spike_probability       NUMERIC(6,5),
    risk_category           VARCHAR(20),
    ci_low                  NUMERIC(6,5),
    ci_high                 NUMERIC(6,5),
    contributing_factors    JSONB,       -- top-5 factors array
    data_provenance         VARCHAR(20) NOT NULL DEFAULT 'PREDICTED',
    twin_state_version      INTEGER NOT NULL,
    model_version           VARCHAR(50) NOT NULL,
    data_quality_warnings   TEXT[],
    feature_vector          JSONB,       -- full feature snapshot for audit
    failure_reason          TEXT,
    triggered_by            VARCHAR(10) NOT NULL,  -- AUTO | MANUAL
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_predictions_patient_id      ON glucose_predictions(patient_id);
CREATE INDEX idx_predictions_predicted_at    ON glucose_predictions(predicted_at DESC);
CREATE INDEX idx_predictions_model_version   ON glucose_predictions(model_version);
CREATE INDEX idx_predictions_patient_time    ON glucose_predictions(patient_id, predicted_at DESC);
```

#### `wearable_events_raw`
```sql
CREATE TABLE wearable_events_raw (
    event_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id          UUID NOT NULL REFERENCES patients(patient_id),
    glucose_reading     NUMERIC(5,2) NOT NULL,
    heart_rate          NUMERIC(6,2),
    hrv                 NUMERIC(6,2),
    sleep_duration      NUMERIC(4,2),
    sleep_stage         VARCHAR(10),
    step_count          INTEGER,
    activity_level      VARCHAR(20),
    event_timestamp     TIMESTAMPTZ NOT NULL,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    processing_status   VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',  -- RECEIVED|PROCESSED|DEAD_LETTERED
    data_source         VARCHAR(20) NOT NULL DEFAULT 'SYNTHETIC'
);
```

#### `dead_letter_events`
```sql
CREATE TABLE dead_letter_events (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL,
    patient_id      UUID,
    payload         JSONB NOT NULL,
    failure_reason  TEXT NOT NULL,
    retry_count     INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

#### `prediction_audit_log`
```sql
CREATE TABLE prediction_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    patient_id_ref  VARCHAR(64) NOT NULL,  -- SHA-256 of patient_id, never raw
    prediction_id   UUID,
    event_type      VARCHAR(50) NOT NULL,
    actor_role      VARCHAR(30),
    trace_id        VARCHAR(64),
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 7.2 Flyway Migration Naming

```
V1__create_patients_table.sql
V2__create_patient_ehr_table.sql
V3__create_digital_twin_states_table.sql
V4__create_glucose_predictions_table.sql
V5__create_wearable_events_raw_table.sql
V6__create_dead_letter_events_table.sql
V7__create_prediction_audit_log_table.sql
V8__add_prediction_indexes.sql
```

### 7.3 JPA Repository Implementations

```
com.glucotwin.infrastructure.persistence/
├── JpaDigitalTwinRepository      implements DigitalTwinRepository
├── JpaPredictionRepository       implements PredictionRepository
├── JpaWearableEventRepository
└── entity mappers/
    ├── DigitalTwinStateMapper
    └── PredictionRecordMapper
```

Optimistic locking via `@Version` on `digital_twin_states.version`. Concurrent updates throw `OptimisticLockingFailureException`, which is caught in the use case and retried once before failing.

---

## 8. Redis Stream Design

### 8.1 Stream Key

```
glucotwin:wearable-events:{patientId}
```

Single consumer group per stream: `twin-state-updater`.

### 8.2 Event Schema (JSON in Redis stream)

```json
{
  "eventId":          "uuid",
  "patientId":        "uuid",
  "glucoseReading":   7.4,
  "heartRate":        72,
  "hrv":              45,
  "sleepDuration":    6.5,
  "sleepStage":       "LIGHT",
  "stepCount":        4200,
  "activityLevel":    "LIGHT",
  "eventTimestamp":   "2026-10-04T11:45:00Z",
  "traceId":          "abc123"
}
```

### 8.3 Consumer Design

```java
// com.glucotwin.infrastructure.messaging.WearableEventConsumer
@Component
public class WearableEventConsumer {
    // Spring Data Redis Stream Listener
    // On message:
    //   1. Deserialise event
    //   2. Call IngestWearableEventUseCase (applies to twin, triggers prediction)
    //   3. ACK message on success
    //   4. On failure: retry with backoff; after max retries → dead-letter table
}
```

Retry policy: exponential backoff (1s, 4s, 16s), max 3 retries (REQ-024).

---

## 9. ML Service API Design (Python / FastAPI)

### 9.1 Prediction Endpoint

```
POST /predict
Content-Type: application/json
X-Internal-Token: <service-to-service secret>

Body: TwinStateSnapshotDTO (full snapshot JSON)

Response 200:
{
  "spikeProbability":       0.72,
  "confidenceInterval":     { "low": 0.61, "high": 0.81 },
  "topContributingFactors": [ ... ],
  "modelVersion":           "xgboost-v1.0.0",
  "imputedFields":          ["hrv_current"]
}

Response 422: { "error": "GLUCOSE_READING_REQUIRED" }
Response 503: { "error": "MODEL_NOT_READY" }
```

### 9.2 Health and Version Endpoints

```
GET /health   → { "status": "ok", "modelVersion": "xgboost-v1.0.0" }
GET /model/info → { "version": "...", "trainedAt": "...", "features": [...] }
```

### 9.3 Service-to-Service Authentication

The ML service is internal only (not reachable from the internet). It accepts requests only from the backend, verified by a shared internal token passed in `X-Internal-Token`. The token value is injected via environment variable `ML_SERVICE_INTERNAL_TOKEN`.

### 9.4 Java HTTP Client for ML Service

```java
// com.glucotwin.infrastructure.ml.MlServicePredictionModel
// Implements PredictionModelPort
public class MlServicePredictionModel implements PredictionModelPort {
    // Uses Spring RestClient (Java 21 / Spring Boot 3.3)
    // Configured timeouts: connect 2s, read 8s
    // On 503: throw PredictionServiceUnavailableException
    // On timeout: throw PredictionTimeoutException
    // On 4xx: throw PredictionInputException
}
```

---

## 10. Data Flow Diagram

```
Doctor / Wearable Simulator
        │
        │ POST /api/v1/patients/{id}/wearable-events
        ▼
WearableEventController
        │
        ▼
IngestWearableEventUseCase
  ├─ Validate WearableEvent
  ├─ Apply to DigitalTwinState → new version
  ├─ Persist DigitalTwinState (PostgreSQL, optimistic lock)
  └─ Publish to Redis Stream
        │
        ▼
WearableEventConsumer (Redis listener)
        │
        ▼
TriggerPredictionUseCase
  ├─ Take TwinStateSnapshot (read tx)
  ├─ Create PredictionRecord (PENDING)
  ├─ Build FeatureVector (passed to ML service)
  ├─ Call MlServicePredictionModel.predict()
  │       │
  │       ▼  [ML Service — Python]
  │   FeatureEngineer.extract()
  │   XGBoostPredictionModel.predict()
  │   SHAP explainer
  │       │
  │       ▼
  │   PredictionResult
  ├─ Derive RiskCategory
  ├─ Update PredictionRecord (COMPLETED)
  ├─ Persist PredictionRecord (PostgreSQL)
  ├─ Write audit log entry
  └─ Publish SSE update event (future dashboard)
        │
        ▼
GET /api/v1/predictions/{predictionId}
        │
        ▼
Doctor Dashboard (future)
```

---

## 11. Error Handling Design

### 11.1 Exception Hierarchy

```
GlucoTwinException (base)
├── ValidationException           → 400
├── ResourceNotFoundException     → 404
├── AccessDeniedException         → 403
├── GlucoseReadingRequiredException → 422
├── TwinStateArchivedError        → 409
├── PredictionServiceUnavailableException → 502
├── PredictionTimeoutException    → 504
└── OptimisticLockRetryExhaustedException → 409
```

### 11.2 Global Exception Handler

```java
// com.glucotwin.api.GlobalExceptionHandler (@RestControllerAdvice)
// Maps each exception type → HTTP status + error code
// Injects traceId from MDC into every error response
// Logs full stack trace at ERROR level server-side only
// Never exposes internal detail to the client
```

### 11.3 Prediction Failure Flow

```
ML call fails
      │
      ▼
Catch PredictionServiceUnavailableException or PredictionTimeoutException
      │
      ▼
Update PredictionRecord: status=FAILED, failureReason=<safe description>
      │
      ▼
Persist FAILED record (separate from COMPLETED records)
      │
      ▼
Log PREDICTION_FAILED at ERROR (patientIdRef hash, traceId, reason)
      │
      ▼
Return 502 or 504 to caller with structured error body
```

---

## 12. Security Design

Follows `security.md` in full.

### 12.1 Authentication Flow

```
Client → JWT (RS256) in Authorization header
       → Spring Security JwtAuthenticationFilter
       → Validates signature against public key (loaded from env: JWT_PUBLIC_KEY)
       → Extracts roles from "roles" claim
       → Sets SecurityContext
       → Request proceeds to controller
```

### 12.2 Row-Level Data Scoping

```java
// PatientAccessEvaluator — custom SpEL expression in @PreAuthorize
@PreAuthorize("hasRole('ADMIN') or @patientAccess.isAssigned(#patientId, authentication)")
public PredictionResponse getPrediction(UUID patientId, UUID predictionId) { ... }
```

The `patient_clinician_assignments` table (out of scope of this feature, assumed to exist) is queried by `PatientAccessEvaluator`.

### 12.3 PII in Logs

All log statements use a `PatientIdRef` wrapper that produces `SHA256(patientId)` when converted to string. Raw `patientId` UUIDs never appear in logs.

---

## 13. Testing Strategy

Aligned with `testing.md`.

### 13.1 Unit Tests (Java)

| Class under test | Test class | Key scenarios |
|---|---|---|
| `DigitalTwinState` | `DigitalTwinStateTest` | applyWearableEvent increments version; markStale sets status; archived state rejects mutations |
| `TriggerPredictionUseCase` | `TriggerPredictionUseCaseTest` | Happy path; ML failure → FAILED record; no glucose → 422; stale twin → warning |
| `RiskCategoryDeriver` | `RiskCategoryDeriverTest` | All four categories; boundary values (0.30, 0.60, 0.85) |
| `FeatureEngineer` (Python) | `test_feature_engineer.py` | All fields present; missing HRV imputed; missing glucose raises error |
| `XGBoostPredictionModel` (Python) | `test_xgboost_model.py` | Output in [0,1]; SHAP values non-empty; CI low ≤ prob ≤ high |

### 13.2 Property-Based Tests

| Property | Tool | Location |
|---|---|---|
| spikeProbability always in [0.0, 1.0] | jqwik (Java) + Hypothesis (Python) | Both layers |
| CI low ≤ spikeProbability ≤ CI high | jqwik + Hypothesis | Both layers |
| twinVersion strictly monotonically increasing | jqwik | Java domain |
| Feature extraction is a pure function (same input = same output) | Hypothesis | Python |
| Imputed field set ⊆ known field names | Hypothesis | Python |

### 13.3 Integration Tests (Java — Testcontainers)

| Scenario | Components exercised |
|---|---|
| Full wearable event → prediction flow | Controller → Redis → Consumer → ML (mock) → DB → API |
| EHR upload persisted and retrievable | Controller → DB → Controller |
| Concurrent twin state updates (10 threads) | Use case → DB (optimistic lock) |
| ML service timeout → 504 response | Mock ML returning timeout → error response |
| Stale twin detection job | Scheduled job → DB → twin status |
| Dead-letter on repeated consumer failure | Redis → Consumer (always fails) → dead_letter_events |

### 13.4 Safety Constraint Tests (REQ-037)

```java
@Tag("safety")
class PredictionSafetyConstraintTest {
    // REQ-025: scan prediction response for forbidden diagnostic terms
    // REQ-026: assert spikeProbability, riskCategory, confidenceInterval all non-null
    // REQ-012: assert dataProvenance == "PREDICTED" for all model outputs
    // REQ-028: assert predictionHorizonHours == 2 in every response
}
```

### 13.5 ML Validation (Python CI)

```python
# scripts/validate_model.py
# Run against held-out synthetic test set
# Assert: AUC-ROC ≥ 0.80, Precision ≥ 0.70, Recall ≥ 0.65, ECE ≤ 0.10
# Fail CI with explicit metric values if any threshold not met
```

---

## 14. Observability Design

### 14.1 Log Events

All logs use JSON format (Logback JSON encoder in Java; Python `logging` with JSON formatter).

| Event | Level | Key fields |
|---|---|---|
| `WEARABLE_EVENT_RECEIVED` | INFO | patientIdRef, eventId, traceId |
| `TWIN_STATE_UPDATED` | INFO | patientIdRef, twinVersion, traceId |
| `PREDICTION_TRIGGERED` | INFO | patientIdRef, predictionId, trigger, traceId |
| `PREDICTION_COMPLETED` | INFO | patientIdRef, predictionId, riskCategory, modelVersion, latencyMs, traceId |
| `PREDICTION_FAILED` | ERROR | patientIdRef, predictionId, failureReason, traceId |
| `TWIN_STATE_STALE` | WARN | patientIdRef, lastEventAt, traceId |
| `DEAD_LETTER_EVENT` | ERROR | eventId, patientIdRef, retryCount, failureReason, traceId |
| `FEATURE_VECTOR_COMPUTED` | DEBUG | predictionId, featureNames, imputedFields, traceId |

### 14.2 Metrics (OpenTelemetry)

```
glucotwin_predictions_total{status, triggered_by, model_version}   Counter
glucotwin_prediction_latency_seconds{stage}                        Histogram
glucotwin_twin_state_version{patient_id_ref}                       Gauge (sampled)
glucotwin_wearable_events_total{patient_id_ref}                    Counter
glucotwin_dead_letter_events_total                                  Counter
glucotwin_stale_twins_total                                         Gauge
glucotwin_ml_service_call_duration_seconds                         Histogram
```

### 14.3 Distributed Tracing

W3C `traceparent` header propagated:
- From ingest API request → Redis message header
- From Redis consumer → ML service HTTP call (via `RestClient` interceptor)
- TraceId included in every log entry via MDC

---

## 15. Extension Points for Future Capabilities

### 15.1 LangGraph / AI Agent Integration

The `TriggerPredictionUseCase` will emit a `PredictionCompletedEvent` domain event. A future `ExplanationAgentUseCase` will subscribe to this event and asynchronously invoke the LangGraph agent to generate a natural-language explanation. The explanation is attached to the `PredictionRecord` as an optional `naturalLanguageExplanation` field. No changes to the prediction pipeline are needed.

### 15.2 MCP Tool Exposure

The ML service already defines a `PredictionModelPort`. Adding MCP tool wrappers is a matter of annotating existing service methods with MCP decorators. The `run_prediction` MCP tool will delegate to `TriggerPredictionUseCase` via the existing internal API. No domain changes required.

### 15.3 RAG Knowledge Layer

A `retrieve_guidelines(query)` tool will be added to the ML service agent. It queries the pgvector store (populated offline from clinical guidelines). The prediction pipeline itself does not change — RAG enriches only the agent-generated natural-language explanation layer.

### 15.4 What-If Simulation

A `SimulatePredictionUseCase` will:
1. Load the current `TwinStateSnapshot` (read-only).
2. Clone it and apply the scenario mutation.
3. Call `TriggerPredictionUseCase` with `dataProvenance: SIMULATED`.
4. Persist the result to the `simulations` table (never `glucose_predictions`).

The `PredictionModelPort` abstraction and feature engineering pipeline are reused without modification. The only new code is the state mutation and the separate persistence path.

---

## 16. Design Decisions and Rationale

| Decision | Rationale | Alternative considered |
|---|---|---|
| Feature engineering lives in ML service (Python), not backend (Java) | Features depend on ML libraries (tsfresh, numpy); keeping them in Python avoids JVM/Python FFI complexity | Java feature engineering with Python model call only; rejected: feature–model coupling risk |
| Backend stores TwinStateSnapshot as JSON in `glucose_predictions.feature_vector` | Full auditability — given any prediction, the exact input is recoverable | Store only the `twinStateVersion` and re-derive on demand; rejected: twin state may have changed |
| Conformal prediction for CI (v1) | Valid coverage guarantee, no ensemble needed, low overhead | Bootstrap ensemble; rejected for v1: high latency; Monte Carlo dropout: requires model change |
| Optimistic locking on `digital_twin_states` | Prevents lost updates under concurrent wearable events without serialising all writes | Pessimistic locking; rejected: higher contention at scale |
| `TwinStateSnapshot` taken inside read transaction | Ensures prediction is always computed on a consistent state | Snapshot after merge; rejected: race condition window |
| Separate `simulations` table from `glucose_predictions` | Enforces provenance separation at the DB level, not just field-level | Single table with `dataProvenance` discriminator; rejected: too easy to query across provenance types accidentally |
