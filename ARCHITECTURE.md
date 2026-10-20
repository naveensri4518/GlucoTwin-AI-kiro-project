# GlucoTwin AI — System Architecture & Data Flow

This document details the architectural principles, microservice topology, data models, state machines, and isolation guarantees of **GlucoTwin AI**.

---

## 1. System Architecture Overview

GlucoTwin AI is structured as a decoupled, reactive microservice architecture composed of five primary tiers:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        React 18 Frontend                               │
│      (Vite 5, TypeScript, Tailwind CSS Dark Theme, TanStack Query)     │
└─────────────────────────────────┬──────────────────────────────────────┘
                                  │ HTTP / REST (/api/v1/*)
                                  ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      Spring Boot 3.3 Backend                           │
│  - REST API Gateway & Security (X-Dev-Auth: ROLE_CLINICIAN)           │
│  - Digital Twin State Management Engine (Static + Dynamic Layers)      │
│  - Redis Stream Producer & Consumer (Wearable Event Pipeline)          │
│  - Multi-Agent Clinical Insight Orchestrator                           │
│  - Counterfactual Simulation Engine                                    │
└────────────┬───────────────────┬───────────────────┬───────────────────┘
             │                   │                   │
             ▼                   ▼                   ▼
    ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
    │  PostgreSQL 16  │ │  Redis Streams  │ │ FastAPI ML Svc  │
    │  (Port 5433)    │ │   (Port 6379)   │ │   (Port 8000)   │
    │                 │ │                 │ │                 │
    │ - Patients      │ │ Stream:         │ │ - XGBoost v1.0  │
    │ - EHR Records   │ │ wearable:events │ │ - SHAP Explainer│
    │ - Twin States   │ │ Group:          │ │ - Probabilities │
    │ - Predictions   │ │ twin-consumers  │ │ - Risk Classes  │
    │ - Audit Logs    │ │                 │ │ - CI Bounds     │
    └─────────────────┘ └─────────────────┘ └─────────────────┘
```

---

## 2. Component Descriptions

### A. React 18 Frontend
- **Design Philosophy:** Inspired by Vercel's restrained monochrome aesthetic (`#000000` canvas, `#111111` surfaces, `#222222` borders, and high-contrast typography).
- **State & Caching:** TanStack Query handles server-state caching, invalidation, and background synchronization.
- **Context Routing:** The top-level `PatientContextSelector` binds clinical context across all active modules (`/predictions`, `/insights`, `/twin`, `/wearables`, `/simulations`).

### B. Spring Boot 3.3 Core Backend
- **Domain-Driven Design (DDD):** Modular core separating application use cases, domain entities, and infrastructure adapters.
- **Event Streaming:** Ingests biometric telemetry via Redis Streams (`glucotwin:wearable-events`) with consumer groups ensuring at-least-once processing.
- **Safety Auditing:** Comprehensive audit logging writing prediction timestamps, input provenance, and clinical disclaimer stamps to PostgreSQL.

### C. FastAPI Machine Learning Service
- **Model:** Pre-trained **XGBoost 2.0.3** binary classifier predicting whether patient glucose will exceed postprandial threshold (≥ 10.0 mmol/L / 180 mg/dL) within 2 hours.
- **Explainability:** TreeExplainer computes exact **SHAP (SHapley Additive exPlanations)** values per feature on every request.
- **Statistical Calibration:** Delivers point probability, 95% confidence intervals, and categorical risk labels (`LOW`, `MODERATE`, `HIGH`, `CRITICAL`).

### D. PostgreSQL Database
- **Migration Strategy:** Version-controlled Flyway migrations (`V1__...` through `V12__...`).
- **Data Model:** Normalized tables for `patients`, `patient_ehr`, `digital_twin_states`, `glucose_predictions`, `simulation_results`, and `prediction_audit_logs`.

---

## 3. Physiological Digital Twin State Machine

The Digital Twin integrates two distinct tiers:

1. **Static EHR Layer:**
   - Populated on patient enrollment via `POST /api/v1/patients/{id}/ehr`.
   - Contains: Date of Birth, Biological Sex, BMI, Diabetes Onset Date, Baseline HbA1c, Fasting Glucose.
   - Frequency: Low-velocity, clinically validated records.

2. **Dynamic Wearable Layer:**
   - Continually updated via Redis stream consumer on incoming wearable events (`POST /api/v1/patients/{id}/wearable-events`).
   - Contains: Rolling CGM glucose window (depth 6–12), Heart Rate, Heart Rate Variability (HRV), Sleep Duration, Sleep Stage, Step Count, Activity Level.
   - Frequency: High-velocity streaming telemetry.

### Status Transitions

```
[Enroll Patient EHR]
         │
         ▼
    INITIALISED ──────────(First Wearable Event)─────────► ACTIVE
         ▲                                                   │
         │                                                   │ (Event age > 60 mins)
         │                                                   ▼
         └─────────────────(New Wearable Event)──────────── STALE
```

- **`INITIALISED`:** Static EHR layer loaded; awaiting initial continuous telemetry.
- **`ACTIVE`:** Streaming wearable data received within the last 60 minutes.
- **`STALE`:** No wearable telemetry received for > 60 minutes; predictions carry data-staleness warning flags.

---

## 4. Counterfactual Simulation & Memory Isolation Guarantee

A fundamental architectural requirement of GlucoTwin AI is **Transactional Memory Isolation**:

- **Scenario Inputs:** Clinicians can test hypothetical carbohydrate intake (0–500g), physical exercise levels (`SEDENTARY`, `LIGHT`, `MODERATE`, `VIGOROUS`), and medication status.
- **Execution Mechanism:**
  1. The backend copies the current observed `DigitalTwinState` into an ephemeral in-memory scenario buffer.
  2. Hypothetical perturbations are applied strictly to the ephemeral copy.
  3. The scenario is forwarded to the ML service to forecast the counterfactual spike trajectory.
  4. The response is persisted in `simulation_results` with `dataProvenance = SIMULATED`.
- **Absolute Guarantee:** The observed `DigitalTwinState` entity and historical wearable observations are never written to or modified during simulation execution.

---

## 5. Data Provenance Taxonomy

To prevent data fabrication and ensure clinical safety, every metric in the system is tagged with one of three explicit provenance labels:

| Category | Definition | Visual Indicator |
| :--- | :--- | :--- |
| **`OBSERVED`** | Actual measured physiological data originating directly from validated EHR records or wearable sensor streams. | Emerald green badge |
| **`PREDICTED`** | Forward-looking statistical risk estimations produced by the XGBoost model pipeline. | Amber / Gold badge |
| **`SIMULATED`** | Hypothetical counterfactual projections resulting from user-specified what-if scenarios. | Violet / Purple badge |

---

## 6. Multi-Agent Clinical Insight Orchestration

The Clinical Insights module coordinates four specialized agents:

1. **`RiskEvidenceAgent`:** Synthesizes observed EHR baselines, active twin state, and latest prediction outputs.
2. **`ClinicalKnowledgeAgent`:** Queries verified medical guidelines (ADA Standards of Care, glycemic variability literature).
3. **`InsightExplanationAgent`:** Generates concise medical narrative. When `OPENAI_API_KEY` is present, it formats structured prompt instructions; otherwise, it executes a deterministic rule-based medical fallback engine.
4. **`InsightVerificationAgent`:** Evaluates generated narrative for numeric consistency, ensures safety disclaimers are intact, and captures the execution trace.
