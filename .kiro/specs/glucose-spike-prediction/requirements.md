# Requirements: Two-Hour Glucose Spike Prediction

## Overview

GlucoTwin AI maintains a per-patient Digital Twin that fuses historical EHR data with simulated real-time wearable/IoT streams. This feature delivers the core clinical value: predicting the probability that a patient's blood glucose will spike significantly within the next two hours, accompanied by a risk category, confidence information, and an explanation of the top contributing factors.

This is a **research and clinical decision-support prototype**. It must not diagnose, prescribe, or assert certainty about medical outcomes.

---

## Requirement Notation

Each requirement uses the following format:

- **ID:** Unique identifier (`REQ-XXX`)
- **Type:** Functional | Data | Safety | Performance | Observability | Testability
- **Priority:** Must / Should / May
- **Statement:** The requirement
- **Rationale:** Why it exists
- **Acceptance Criteria:** Specific, testable conditions

---

## 1. Functional Requirements

### REQ-001 — EHR Data Ingestion
- **Type:** Functional | **Priority:** Must
- **Statement:** The system must accept and persist structured EHR data for a patient, including: demographics (age, sex, BMI), Type 2 Diabetes diagnosis history, historical glucose measurements (HbA1c, fasting glucose), current medications (name, dose, frequency), and relevant laboratory results (HbA1c, lipids, renal function markers).
- **Rationale:** The static layer of the Digital Twin cannot be built without a structured patient history.
- **Acceptance Criteria:**
  - A `POST /api/v1/patients/{patientId}/ehr` request with a valid EHR payload returns `201 Created`.
  - All EHR fields are persisted to the `patient_ehr` table and retrievable via `GET /api/v1/patients/{patientId}/ehr`.
  - A partial EHR payload (missing optional fields) is accepted and stored with null/absent values for missing fields.
  - An EHR payload with missing required fields returns `400 Bad Request` with a structured validation error.
  - All persisted EHR data is labelled with `dataProvenance: OBSERVED`.

### REQ-002 — Wearable Event Ingestion
- **Type:** Functional | **Priority:** Must
- **Statement:** The system must accept real-time (or simulated) wearable data events for a patient, containing: current glucose reading (mmol/L), heart rate (bpm), heart rate variability (ms), sleep duration (hours), sleep stage (AWAKE / LIGHT / DEEP / REM), step count (daily total), activity level (SEDENTARY / LIGHT / MODERATE / VIGOROUS), and an event timestamp (ISO 8601 UTC).
- **Rationale:** The dynamic layer of the Digital Twin is driven by continuous wearable events. Without them, the twin becomes stale and predictions degrade.
- **Acceptance Criteria:**
  - A `POST /api/v1/patients/{patientId}/wearable-events` request with a valid payload returns `202 Accepted`.
  - The event is pushed to the Redis stream within 200ms of receipt.
  - A backend consumer reads the event from Redis and merges it into the patient's `DigitalTwinState` within 1 second of the event being placed on the stream.
  - All wearable event data is labelled `dataProvenance: OBSERVED`.
  - An event with an out-of-range glucose value (< 1.0 or > 35.0 mmol/L) is accepted but the twin state is updated with `dataQualityFlag: DATA_QUALITY_WARNING` on the affected field.

### REQ-003 — Digital Twin State Management
- **Type:** Functional | **Priority:** Must
- **Statement:** The system must maintain a per-patient `DigitalTwinState` that is continuously updated as new EHR data and wearable events arrive. The state must track both static (EHR) and dynamic (wearable) layers separately, and carry a monotonically increasing version number.
- **Rationale:** The prediction model operates on a unified state snapshot. Without a versioned, consistently updated state object, predictions cannot be traced back to the data that produced them.
- **Acceptance Criteria:**
  - A `DigitalTwinState` record exists in the database for every patient who has had at least one EHR upload or wearable event.
  - Each state update increments the `twinVersion` by exactly 1.
  - The state records the `lastUpdatedAt` timestamp on every mutation.
  - The state carries a `status` field with values: `INITIALISED` (EHR loaded, no wearable yet), `ACTIVE` (wearable events flowing), `STALE` (no wearable event in > 30 minutes), `ARCHIVED`.
  - Concurrent wearable events for the same patient are serialised; no lost updates occur under concurrent write load.
  - A `GET /api/v1/patients/{patientId}/twin-state` endpoint returns the current state with all fields.

### REQ-004 — Trigger Prediction on State Update
- **Type:** Functional | **Priority:** Must
- **Statement:** The system must automatically trigger a new prediction whenever the `DigitalTwinState` is updated by a wearable event. Predictions may also be triggered on-demand via an explicit API call.
- **Rationale:** Clinicians need predictions that reflect the current patient state. Stale predictions based on old data reduce clinical value and could mislead.
- **Acceptance Criteria:**
  - Every wearable event ingestion that results in a twin state update produces a corresponding prediction record within 3 seconds (p95) of the event being received.
  - A `POST /api/v1/patients/{patientId}/predictions` request triggers an on-demand prediction and returns `202 Accepted` with a `predictionId`.
  - Prediction is NOT triggered when only the EHR is updated (no dynamic signal — spike prediction requires wearable context).
  - If the twin state is `STALE`, the prediction is produced but includes a `dataQualityWarning: STALE_WEARABLE_DATA` flag in the response.

### REQ-005 — Glucose Spike Prediction Output
- **Type:** Functional | **Priority:** Must
- **Statement:** Every prediction result must contain the following fields:
  - `predictionId` (UUID)
  - `patientId` (UUID)
  - `predictedAt` (ISO 8601 UTC timestamp)
  - `predictionHorizonHours` (fixed: `2`)
  - `spikeProbability` (float, range [0.0, 1.0])
  - `riskCategory` (enum: `LOW` / `MODERATE` / `HIGH` / `CRITICAL`)
  - `confidenceInterval` (`{ low: float, high: float }`, 95% CI)
  - `topContributingFactors` (list of ≤ 5 `{ factorName: string, contribution: float, direction: INCREASES_RISK | DECREASES_RISK }`)
  - `dataProvenance` (enum: `OBSERVED` | `PREDICTED` | `SIMULATED`)
  - `twinStateVersion` (integer — the version of the `DigitalTwinState` snapshot used)
  - `modelVersion` (string — the version of the ML model artefact used)
  - `dataQualityWarnings` (list of strings, may be empty)
- **Rationale:** All output fields are required to support clinical review, audit, explainability, and safety labelling.
- **Acceptance Criteria:**
  - Every prediction response from `GET /api/v1/predictions/{predictionId}` contains all listed fields.
  - `spikeProbability` is always in [0.0, 1.0]; any model output outside this range is clamped and logged as an anomaly.
  - `dataProvenance` is always `PREDICTED` for model-generated predictions.
  - `dataProvenance` is always `SIMULATED` for what-if scenario predictions (future extension, reserved field).
  - `twinStateVersion` always matches the version recorded in the `DigitalTwinState` snapshot at prediction time.
  - `modelVersion` is never null or empty.

### REQ-006 — Risk Category Derivation
- **Type:** Functional | **Priority:** Must
- **Statement:** The system must derive a `riskCategory` from `spikeProbability` using defined, configurable thresholds.
- **Rationale:** Clinicians need a categorical risk signal in addition to a raw probability. Thresholds must be configurable without code changes to allow clinical tuning.
- **Acceptance Criteria:**
  - Default thresholds:
    - `LOW`: probability < 0.30
    - `MODERATE`: 0.30 ≤ probability < 0.60
    - `HIGH`: 0.60 ≤ probability < 0.85
    - `CRITICAL`: probability ≥ 0.85
  - Thresholds are loaded from application configuration at startup; changing them requires only a config change and restart, not code changes.
  - The derived category is consistent with the probability in every prediction record.
  - Boundary values (exactly 0.30, 0.60, 0.85) always fall into the upper category (MODERATE, HIGH, CRITICAL respectively).

### REQ-007 — Prediction Retrieval
- **Type:** Functional | **Priority:** Must
- **Statement:** The API must allow retrieval of a single prediction by ID and a paginated list of predictions for a patient.
- **Rationale:** Clinicians need to review prediction history and compare trends over time.
- **Acceptance Criteria:**
  - `GET /api/v1/predictions/{predictionId}` returns the full prediction object or `404` if not found.
  - `GET /api/v1/patients/{patientId}/predictions` returns a paginated list (default page size 20, max 100), ordered by `predictedAt` descending.
  - The list endpoint supports `?from=<ISO8601>&to=<ISO8601>` date range filtering.
  - Predictions for a different patient than the authenticated clinician's assigned patients return `403 Forbidden`.

### REQ-008 — Stale Twin State Handling
- **Type:** Functional | **Priority:** Must
- **Statement:** If no wearable event has been received for a patient in more than 30 minutes, the twin state must transition to `STALE`. Any prediction produced while the state is `STALE` must include a `dataQualityWarning`.
- **Rationale:** Predictions based on old wearable data carry higher uncertainty. Clinicians must be explicitly informed.
- **Acceptance Criteria:**
  - A background job checks for stale twins every 5 minutes and updates the `status` field to `STALE` for qualifying patients.
  - The staleness threshold (30 minutes) is configurable.
  - A prediction produced from a `STALE` twin always contains `"STALE_WEARABLE_DATA"` in `dataQualityWarnings`.
  - The UI (future) and API always expose the current `twinStatus` alongside any prediction.

---

## 2. Data Validation Requirements

### REQ-009 — EHR Field Validation
- **Type:** Data | **Priority:** Must
- **Statement:** All EHR fields must be validated at the API boundary before persistence.
- **Rationale:** Invalid EHR data corrupts the static layer and produces misleading predictions.
- **Acceptance Criteria:**
  - Required fields: `patientId` (UUID), `dateOfBirth` (past date), `sex` (MALE / FEMALE / OTHER), `diabetesOnsetDate` (past date).
  - Optional with range constraints: `bmi` (10.0–80.0 kg/m²), `hba1c` (3.0–20.0 %), `fastingGlucose` (1.0–35.0 mmol/L).
  - A request with `bmi: -5` returns `400` with `{ "field": "bmi", "error": "OUT_OF_RANGE" }`.
  - A request with an unrecognised `sex` value returns `400`.
  - Future dates for `dateOfBirth` or `diabetesOnsetDate` return `400`.

### REQ-010 — Wearable Event Field Validation
- **Type:** Data | **Priority:** Must
- **Statement:** All wearable event fields must be validated at the API boundary. Out-of-range values must be flagged, not silently accepted.
- **Rationale:** Sensor noise and transmission errors produce physiologically impossible values. These must not enter the prediction pipeline without flagging.
- **Acceptance Criteria:**
  - Physiological ranges (hard reject below floor, warn above ceiling):
    - `glucoseReading`: 1.0–35.0 mmol/L → outside range: `400 Bad Request`
    - `heartRate`: 20–300 bpm → outside range: `400 Bad Request`
    - `hrv`: 0–300 ms → outside range: `400 Bad Request`
    - `sleepDuration`: 0–24 hours → outside range: `400 Bad Request`
    - `stepCount`: 0–100,000 steps → outside range: `400 Bad Request`
  - `sleepStage` must be one of: `AWAKE`, `LIGHT`, `DEEP`, `REM`.
  - `activityLevel` must be one of: `SEDENTARY`, `LIGHT`, `MODERATE`, `VIGOROUS`.
  - `eventTimestamp` must not be more than 24 hours in the past or in the future.
  - A future timestamp returns `400`.
  - An event timestamp > 24 hours old is accepted but logged with `dataQualityWarning: DELAYED_EVENT`.

### REQ-011 — Missing Data Handling
- **Type:** Data | **Priority:** Must
- **Statement:** The prediction pipeline must handle missing optional fields explicitly. It must not fail silently or propagate `null`/`NaN` into model inputs.
- **Rationale:** Real wearable devices drop readings. A model that crashes on missing data is not production-worthy.
- **Acceptance Criteria:**
  - Each optional wearable field that is absent is substituted with a defined population median value (configured, not hardcoded) and the `dataQualityWarnings` list includes `"IMPUTED_FIELD:<fieldName>"` for each imputed field.
  - If `glucoseReading` (the primary signal) is missing, the prediction is not run; the API returns `422 Unprocessable Entity` with `"GLUCOSE_READING_REQUIRED"`.
  - The feature engineering layer records the set of imputed fields in the prediction record for audit purposes.

### REQ-012 — Data Provenance Labelling
- **Type:** Data | **Priority:** Must
- **Statement:** Every data item that flows through the system — EHR fields, wearable events, twin state fields, prediction outputs — must carry an explicit `dataProvenance` label: `OBSERVED`, `PREDICTED`, or `SIMULATED`.
- **Rationale:** Core safety boundary from `product.md`. Mixing unlabelled data is a defect.
- **Acceptance Criteria:**
  - EHR and wearable event data is always `OBSERVED`.
  - Model output (spike probability, risk category) is always `PREDICTED`.
  - What-if scenario outputs (reserved, future) are always `SIMULATED`.
  - The API never returns a prediction response that omits the `dataProvenance` field.
  - Automated tests verify all three label values are present and mutually exclusive across their respective data types.

---

## 3. Digital Twin State Requirements

### REQ-013 — Twin State Versioning
- **Type:** Functional | **Priority:** Must
- **Statement:** Every mutation to the `DigitalTwinState` must produce a new version. The version used for a prediction must be recorded alongside the prediction.
- **Rationale:** Enables full audit traceability: given any prediction, the exact data state that produced it can be reconstructed.
- **Acceptance Criteria:**
  - `twinVersion` starts at `1` on creation and increments by `1` on each update.
  - The `twinVersion` stored in a `PredictionRecord` matches the `twinVersion` of the `DigitalTwinState` snapshot at prediction time.
  - Reading a prediction record always returns the `twinStateVersion` it was computed from.

### REQ-014 — Twin State Persistence
- **Type:** Functional | **Priority:** Must
- **Statement:** The current `DigitalTwinState` must be persisted in PostgreSQL. The system must be recoverable: after a restart, the twin state is restored from the database, not lost.
- **Rationale:** In-memory-only twin state would be lost on restart, breaking continuity for active patients.
- **Acceptance Criteria:**
  - After a service restart, `GET /api/v1/patients/{patientId}/twin-state` returns the same state as before the restart.
  - The twin state table uses optimistic locking (version column) to prevent concurrent update anomalies.
  - A Flyway migration creates and evolves the `digital_twin_states` table.

### REQ-015 — Twin State Snapshot for Prediction
- **Type:** Functional | **Priority:** Must
- **Statement:** The prediction engine must receive an immutable snapshot of the `DigitalTwinState` at the moment prediction is triggered. The snapshot must not be affected by any concurrent state updates.
- **Rationale:** If the state changes mid-prediction, the prediction result would not correspond to any stable state, breaking auditability.
- **Acceptance Criteria:**
  - The snapshot is taken inside a read transaction; no partial updates are visible.
  - The `twinStateVersion` recorded in the prediction corresponds exactly to the snapshot version, not a later one.
  - Concurrent state updates during a prediction computation do not change the prediction result.

---

## 4. Prediction Behaviour Requirements

### REQ-016 — ML Model Abstraction
- **Type:** Functional | **Priority:** Must
- **Statement:** The prediction engine must use a replaceable ML model abstraction (interface/port). The concrete model implementation (XGBoost in v1) must be swappable without changing the Digital Twin domain logic or REST API contract.
- **Rationale:** The prediction model will evolve. Coupling it tightly to the domain would make every model upgrade a breaking change.
- **Acceptance Criteria:**
  - A `PredictionModelPort` interface (or equivalent) defines `predict(TwinStateSnapshot) → PredictionResult`.
  - The `XGBoostPredictionModel` is one implementation of this interface.
  - A `MockPredictionModel` implementing the same interface is used in unit and integration tests (no real ML model needed for backend tests).
  - Swapping implementations requires only a configuration/wiring change, not a code change in the domain or API layers.

### REQ-017 — Feature Engineering
- **Type:** Functional | **Priority:** Must
- **Statement:** The system must extract a defined set of features from the `DigitalTwinState` snapshot before passing them to the prediction model.
- **Rationale:** The model operates on engineered features, not raw fields. Feature logic must be explicit, testable, and versioned.
- **Acceptance Criteria:**
  - The following features are computed (v1 set):
    - `cgm_current`: latest glucose reading
    - `cgm_delta_30m`: change in glucose over last 30 minutes
    - `cgm_mean_60m`: rolling mean glucose over last 60 minutes
    - `cgm_slope_60m`: linear trend slope of glucose over last 60 minutes
    - `heart_rate_current`: latest heart rate
    - `hrv_current`: latest HRV
    - `sleep_duration_last`: most recent sleep duration
    - `step_count_today`: daily step total
    - `activity_level_encoded`: ordinal encoding of activity level (0–3)
    - `hba1c_latest`: most recent HbA1c from EHR
    - `bmi`: from EHR demographics
    - `time_of_day_sin` / `time_of_day_cos`: cyclical encoding of hour of day
    - `days_since_diagnosis`: derived from `diabetesOnsetDate`
  - Feature computation is a pure function: same input always produces same output.
  - Feature names and their computation logic are documented in `docs/data-dictionary.md`.
  - The full feature vector is logged (at DEBUG level) with each prediction for diagnostic purposes.

### REQ-018 — Prediction Confidence Interval
- **Type:** Functional | **Priority:** Must
- **Statement:** Every prediction must include a 95% confidence interval around the `spikeProbability`.
- **Rationale:** A point probability without uncertainty bounds cannot responsibly communicate risk to a clinician.
- **Acceptance Criteria:**
  - `confidenceInterval.low` ≤ `spikeProbability` ≤ `confidenceInterval.high`.
  - Both bounds are in [0.0, 1.0].
  - The interval width is always > 0 (degenerate point intervals are a model defect).
  - Confidence interval computation method is documented in `docs/data-dictionary.md`.

### REQ-019 — Prediction Model Version Recording
- **Type:** Functional | **Priority:** Must
- **Statement:** Every prediction must record the exact version of the model artefact that produced it.
- **Rationale:** If a model defect is later discovered, all affected predictions must be identifiable and potentially invalidated.
- **Acceptance Criteria:**
  - `modelVersion` is a non-empty string (e.g., `"xgboost-v1.2.0"`).
  - The model artefact file includes its version in metadata or filename.
  - A query `GET /api/v1/predictions?modelVersion=xgboost-v1.0.0` returns all predictions from that model version.

---

## 5. API Requirements

### REQ-020 — REST API Contract
- **Type:** Functional | **Priority:** Must
- **Statement:** All prediction-related endpoints must follow the REST conventions defined in `coding-standards.md` and be documented via OpenAPI 3.1.
- **Rationale:** The API is the contract between backend and frontend (doctor dashboard). It must be stable, versioned, and self-describing.
- **Acceptance Criteria:**
  - All endpoints are under `/api/v1/`.
  - OpenAPI spec is published at `/api/docs` and kept in sync with the implementation.
  - All endpoints require JWT authentication (RS256, except `/api/health`).
  - Response bodies use camelCase JSON field names.
  - Date-times use ISO 8601 UTC format.

  | Method | Path | Description |
  |---|---|---|
  | POST | `/api/v1/patients/{patientId}/ehr` | Upload/replace EHR data |
  | GET | `/api/v1/patients/{patientId}/ehr` | Retrieve current EHR data |
  | POST | `/api/v1/patients/{patientId}/wearable-events` | Ingest a wearable event |
  | GET | `/api/v1/patients/{patientId}/twin-state` | Get current Digital Twin state |
  | POST | `/api/v1/patients/{patientId}/predictions` | Trigger on-demand prediction |
  | GET | `/api/v1/patients/{patientId}/predictions` | List predictions (paginated) |
  | GET | `/api/v1/predictions/{predictionId}` | Get single prediction |

### REQ-021 — API Error Responses
- **Type:** Functional | **Priority:** Must
- **Statement:** All API error responses must use a consistent structured format and must never expose stack traces, internal class names, or SQL to the client.
- **Rationale:** Consistency enables the frontend to handle errors uniformly. Leaking internals is a security risk.
- **Acceptance Criteria:**
  - Error response format:
    ```json
    {
      "error": "VALIDATION_ERROR",
      "message": "glucoseReading must be between 1.0 and 35.0",
      "timestamp": "2026-10-04T12:00:00Z",
      "traceId": "abc123"
    }
    ```
  - `traceId` is always populated (from distributed trace context).
  - `500` responses always use a generic `"INTERNAL_ERROR"` code — never expose exception messages.
  - All 4xx/5xx paths are covered by integration tests.

### REQ-022 — API Authentication and Authorisation
- **Type:** Functional | **Priority:** Must
- **Statement:** All prediction endpoints require a valid JWT (RS256). Clinicians may only access predictions for their assigned patients.
- **Rationale:** Defined in `security.md`. Clinical data access must be scoped per clinician.
- **Acceptance Criteria:**
  - A request without a JWT returns `401 Unauthorized`.
  - A request with an expired or invalid JWT returns `401 Unauthorized`.
  - A clinician requesting predictions for an unassigned patient returns `403 Forbidden`.
  - An admin (`ROLE_ADMIN`) may access all patients' predictions.

---

## 6. Error Handling Requirements

### REQ-023 — Prediction Pipeline Failure Handling
- **Type:** Functional | **Priority:** Must
- **Statement:** If the ML model call fails (service unavailable, timeout, invalid response), the system must record the failure, return a structured error to the caller, and must not persist a partial or invalid prediction record.
- **Rationale:** A failed prediction silently stored as a real prediction would mislead clinicians.
- **Acceptance Criteria:**
  - A failed ML call results in a `PredictionRecord` with `status: FAILED` and a `failureReason` field — it is never stored with `status: COMPLETED`.
  - The API returns `502 Bad Gateway` (ML service unavailable) or `504 Gateway Timeout` (ML service timed out) with a structured error body.
  - A failed prediction triggers an `ERROR` log entry with the `patientId` reference (not PII), `traceId`, and `failureReason`.
  - The twin state is NOT rolled back on prediction failure — it remains at the updated version.

### REQ-024 — Wearable Event Processing Failure
- **Type:** Functional | **Priority:** Must
- **Statement:** If the Redis consumer fails to process a wearable event, the event must be retried with exponential backoff (max 3 retries). After 3 failures, the event is moved to a dead-letter queue and an alert is raised.
- **Rationale:** Lost wearable events without a retry mechanism would silently degrade the twin state.
- **Acceptance Criteria:**
  - Failed Redis consumer processing retries after: 1s, 4s, 16s (exponential backoff, base 2, initial 1s).
  - After 3 failed retries, the event is written to a `dead_letter_events` table with the failure reason.
  - An `ERROR` log is emitted for each retry and the dead-letter event.
  - A `GET /api/v1/admin/dead-letter-events` endpoint (ROLE_ADMIN only) lists unprocessed dead-letter events.

---

## 7. Safety Requirements

### REQ-025 — No Diagnostic Language
- **Type:** Safety | **Priority:** Must
- **Statement:** No API response, log message, or system-generated text may contain language that asserts a medical diagnosis, prescribes a treatment, or guarantees a medical outcome.
- **Rationale:** Core safety boundary from `product.md`. This system is decision-support, not a diagnostic tool.
- **Acceptance Criteria:**
  - An automated test scans all prediction API response fields for forbidden terms: `diagnose`, `diagnosis`, `you have`, `confirms`, `prescribe`, `prescription`, `take [dose]`, `guaranteed`, `certain`.
  - The test runs in CI and fails the build if any forbidden term is found in a real API response.
  - Model explanation text (future AI agent output) is filtered through the output safety filter before inclusion in any response.

### REQ-026 — Uncertainty Communication
- **Type:** Safety | **Priority:** Must
- **Statement:** Every prediction response must communicate uncertainty. A bare probability without a confidence interval or risk category is insufficient.
- **Rationale:** A single probability number presented without context could be misinterpreted as a certain prediction.
- **Acceptance Criteria:**
  - Every prediction response includes `spikeProbability`, `riskCategory`, and `confidenceInterval` — all three are mandatory; none may be null.
  - The API documentation explicitly states: "spikeProbability is a probabilistic estimate, not a certainty."
  - The confidence interval width is always > 0.

### REQ-027 — Synthetic and Anonymised Data Policy
- **Type:** Safety | **Priority:** Must
- **Statement:** All patient data used in development, testing, CI, and demos must be synthetic, publicly available and de-identified, or explicitly anonymised. No real patient data may enter any environment.
- **Rationale:** From `security.md`. Even prototype systems with real patient data carry regulatory and ethical obligations.
- **Acceptance Criteria:**
  - All seed data in `data/` carries a `dataSource: SYNTHETIC` metadata tag.
  - CI pipeline seed scripts use only the synthetic data from `data/`.
  - A test asserts that no seed record has `dataSource` other than `SYNTHETIC` or `ANONYMISED`.
  - Documentation explicitly states the data policy.

### REQ-028 — Prediction Horizon Clarity
- **Type:** Safety | **Priority:** Must
- **Statement:** The prediction horizon (2 hours) must be explicitly stated in every prediction response. The system must not produce predictions for a different horizon without a new, explicitly versioned model and API change.
- **Rationale:** A clinician must know the exact timeframe the prediction covers to act appropriately.
- **Acceptance Criteria:**
  - `predictionHorizonHours: 2` is present in every prediction response.
  - The field is immutable in v1 — the API rejects any request attempting to specify a custom horizon.
  - Future horizon configurability is a v2 feature and requires a new model artefact.

---

## 8. Performance Requirements

### REQ-029 — End-to-End Prediction Latency
- **Type:** Performance | **Priority:** Must
- **Statement:** From the moment a wearable event is received by the API to the moment the prediction result is available via `GET /api/v1/predictions/{predictionId}`, the total elapsed time must be ≤ 3 seconds at p95 under normal load.
- **Rationale:** From `product.md` success criteria. Clinical decision support is only useful if results are timely.
- **Acceptance Criteria:**
  - Load test (50 concurrent patients, continuous wearable stream) demonstrates p95 latency ≤ 3 seconds.
  - p99 latency is logged and monitored; alerting threshold is 8 seconds.
  - Each stage of the pipeline (ingest, Redis enqueue, consumer, feature engineering, ML call, persist) is instrumented with individual latency metrics.

### REQ-030 — On-Demand Prediction Latency
- **Type:** Performance | **Priority:** Must
- **Statement:** An on-demand prediction triggered via `POST /api/v1/patients/{patientId}/predictions` must return the prediction result within 3 seconds (p95).
- **Rationale:** Doctors triggering manual predictions during a consultation need a responsive system.
- **Acceptance Criteria:**
  - The `POST` returns `202 Accepted` immediately with a `predictionId`.
  - `GET /api/v1/predictions/{predictionId}` returns a completed result within 3 seconds (p95) of the `POST`.
  - A `status` field on the prediction (`PENDING` / `COMPLETED` / `FAILED`) enables polling.

### REQ-031 — Throughput
- **Type:** Performance | **Priority:** Should
- **Statement:** The system should support at least 100 concurrent active patients sending wearable events at 1 event per minute without degradation in prediction latency.
- **Rationale:** Establishes a baseline concurrency target for the prototype.
- **Acceptance Criteria:**
  - A load test with 100 simulated patients at 1 event/minute sustains p95 latency ≤ 3 seconds.
  - No prediction records are lost or duplicated during the load test.

---

## 9. Observability Requirements

### REQ-032 — Structured Logging
- **Type:** Observability | **Priority:** Must
- **Statement:** Every significant event in the prediction pipeline must produce a structured (JSON) log entry containing: `timestamp`, `level`, `service`, `traceId`, `spanId`, `patientIdRef` (hashed), `event`, and relevant metadata.
- **Rationale:** Structured logs enable filtering, alerting, and debugging in production without exposing PII.
- **Acceptance Criteria:**
  - Log events required:
    - `WEARABLE_EVENT_RECEIVED`
    - `TWIN_STATE_UPDATED`
    - `PREDICTION_TRIGGERED`
    - `PREDICTION_COMPLETED`
    - `PREDICTION_FAILED`
    - `TWIN_STATE_STALE`
    - `DEAD_LETTER_EVENT`
  - `patientIdRef` is a SHA-256 hash of the `patientId` — never the raw UUID.
  - All log entries include a `traceId` for distributed trace correlation.

### REQ-033 — Metrics
- **Type:** Observability | **Priority:** Must
- **Statement:** The system must expose metrics for: prediction count (by status), prediction latency (histogram), twin state update count, wearable event ingest count, dead-letter event count, and model version in use.
- **Rationale:** Metrics drive operational monitoring and SLA verification.
- **Acceptance Criteria:**
  - Metrics are exposed in OpenTelemetry format and shipped to AWS CloudWatch.
  - A `prediction_latency_seconds` histogram metric is present with `p50`, `p95`, `p99` buckets.
  - A `dead_letter_event_total` counter increments on every dead-letter event.
  - Metrics are available within 60 seconds of the event occurring.

### REQ-034 — Distributed Tracing
- **Type:** Observability | **Priority:** Should
- **Statement:** The system should propagate a distributed trace context (W3C TraceContext) across the full prediction pipeline: from the ingest API call → Redis → backend consumer → ML service call → response.
- **Rationale:** Without trace propagation, debugging cross-service latency issues requires manual log correlation.
- **Acceptance Criteria:**
  - The `traceId` in the prediction response matches the `traceId` of the originating wearable event ingest request.
  - Traces are visible in AWS X-Ray or a compatible OpenTelemetry backend.

---

## 10. Testability Requirements

### REQ-035 — Mock ML Model
- **Type:** Testability | **Priority:** Must
- **Statement:** A mock implementation of the `PredictionModelPort` must exist for use in backend unit and integration tests. It must be configurable to return specific probability values or simulate failure.
- **Rationale:** Backend tests must not depend on the Python ML service running. The mock enables isolated, fast, deterministic tests.
- **Acceptance Criteria:**
  - `MockPredictionModel` can be configured to return a fixed probability (e.g., `0.75`).
  - `MockPredictionModel` can be configured to throw a defined exception (simulating ML service failure).
  - All integration tests that test the prediction pipeline use the mock by default.

### REQ-036 — Test Data Factory
- **Type:** Testability | **Priority:** Must
- **Statement:** A test data factory must exist that generates valid `DigitalTwinState` snapshots, valid EHR payloads, and valid wearable event payloads for use in tests.
- **Rationale:** Duplicated test setup code creates brittle tests and makes it hard to update data shapes. A factory ensures consistency.
- **Acceptance Criteria:**
  - `TwinStateSnapshotFactory.create()` returns a valid snapshot with all required fields populated.
  - Factory methods accept overrides for specific fields (e.g., `create(glucoseReading: 22.0)`).
  - All factories are documented in the test package README.

### REQ-037 — Safety Constraint Tests
- **Type:** Testability | **Priority:** Must
- **Statement:** Automated tests must verify all safety constraints in REQ-025 (no diagnostic language), REQ-026 (uncertainty fields present), and REQ-012 (data provenance labels) against real API responses.
- **Rationale:** Safety constraints that are only enforced by convention (not tests) will eventually be violated.
- **Acceptance Criteria:**
  - A dedicated test class `PredictionSafetyConstraintTest` covers all three safety requirements.
  - These tests run in CI and are tagged `@Tag("safety")` so they can be reported separately.
  - CI fails on any safety test failure, regardless of the failure mode.

---

## Glossary

| Term | Definition |
|---|---|
| CGM | Continuous Glucose Monitor — the primary wearable glucose sensor |
| Digital Twin | The per-patient persistent virtual state combining EHR and wearable data |
| EHR | Electronic Health Record — structured historical patient data |
| Glucose spike | A blood glucose level exceeding a clinical threshold (default: 10.0 mmol/L) within the prediction horizon |
| HbA1c | Glycated haemoglobin — a 3-month average glucose marker |
| HRV | Heart Rate Variability — autonomic nervous system marker |
| OBSERVED | Data provenance label: actual measured/recorded data |
| PREDICTED | Data provenance label: model-generated probabilistic output |
| SIMULATED | Data provenance label: hypothetical what-if scenario data |
| Twin state version | Monotonically increasing integer tracking mutations to the Digital Twin |
