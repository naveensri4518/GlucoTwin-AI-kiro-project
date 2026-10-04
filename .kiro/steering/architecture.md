---
inclusion: always
---

# GlucoTwin AI — Architecture Steering

## System Overview

GlucoTwin AI is a **multi-service system** with three deployable units and a shared data tier.

```
┌─────────────────────────────────────────────────────────────┐
│  Doctor Dashboard  (React + TypeScript)                     │
└─────────────────────────┬───────────────────────────────────┘
                          │ REST / SSE
┌─────────────────────────▼───────────────────────────────────┐
│  Backend API  (Java 21 + Spring Boot)                       │
│  - Patient / Twin state management                          │
│  - Data ingestion & validation                              │
│  - Orchestration of ML calls                                │
│  - Audit logging                                            │
└──────┬───────────────────────────────────┬──────────────────┘
       │ REST                              │ Redis pub/sub
┌──────▼──────────────────┐   ┌───────────▼──────────────────┐
│  ML Service  (Python /  │   │  Wearable Stream Buffer       │
│  FastAPI)               │   │  (Redis 7)                    │
│  - Prediction engine    │   └──────────────────────────────┘
│  - LangGraph agents     │
│  - RAG retriever        │
│  - MCP server           │
│  - What-if simulation   │
└──────┬──────────────────┘
       │
┌──────▼──────────────────────────────────────────────────────┐
│  PostgreSQL 16  (+ pgvector extension)                      │
│  - Patient & twin state tables                              │
│  - Predictions & audit log                                  │
│  - RAG embedding vectors                                    │
└─────────────────────────────────────────────────────────────┘
```

---

## End-to-End Data Flow

```
1. EHR upload (REST, JSON/CSV)
        │
        ▼
2. Backend: schema validation → normalisation → PatientState upsert (PostgreSQL)
        │
        ▼
3. Wearable event (POST /ingest/wearable or simulated generator)
        │
        ▼
4. Backend: validate → push to Redis stream
        │
        ▼
5. Backend consumer: pop Redis → merge into DigitalTwinState → persist
        │
        ▼
6. Backend: call ML service POST /predict with current twin state snapshot
        │
        ▼
7. ML service: feature engineering → XGBoost model → spike probability + SHAP values
        │
        ▼
8. Backend: store prediction → push update event via SSE to dashboard
        │
        ▼
9. Dashboard: render twin state, prediction, explanation
```

---

## Digital Twin Lifecycle

```
CREATE         → Patient registered; EHR loaded; initial static state built
ACTIVATE       → First wearable event received; dynamic layer initialised
RUNNING        → Continuous wearable stream; state updated per event; predictions scheduled
STALE          → No wearable event for > configured threshold (flag in UI)
SIMULATED      → Doctor triggers what-if; a state copy is mutated; prediction runs on copy
ARCHIVED       → Patient discharged or study ended; state frozen, read-only
```

### State Object (conceptual fields)

```
DigitalTwinState {
  patientId
  status: CREATE | ACTIVE | RUNNING | STALE | SIMULATED | ARCHIVED
  staticLayer {
    demographics, diagnoses (ICD codes), medications, labResults, allergies
  }
  dynamicLayer {
    latestCgmReading, cgmTrend, stepCount, heartRateResting, sleepHours,
    lastUpdatedAt, dataQualityScore
  }
  predictionLayer {
    spikeProbability, confidenceInterval, predictionWindowHours,
    shapValues, predictedAt, modelVersion
  }
  metadata {
    twinVersion, createdAt, lastModifiedAt
  }
}
```

---

## EHR vs Wearable Data Separation

| Dimension | EHR / Static | Wearable / Dynamic |
|---|---|---|
| Update frequency | Days to months | Minutes to seconds |
| Ingestion path | REST upload endpoint | Redis stream → consumer |
| Storage | Relational tables (normalised) | Time-series rows + latest snapshot |
| Validation | Schema + clinical range checks | Range checks + spike/drop detection |
| Label | `OBSERVED` | `OBSERVED` (real) or `SIMULATED` (synthetic) |
| Personally sensitive | Yes — treat with highest data care | Yes — treat with highest data care |

The two layers **never** merge into a single raw table. They are joined only at the Digital Twin state level.

---

## Prediction Architecture

```
DigitalTwinState snapshot
        │
        ▼
Feature Engineering (ml-service/features/)
  - Rolling statistics on CGM (mean, std, slope over 30/60/120 min)
  - Time-of-day, day-of-week encoding
  - Lab result recency weighting
  - Medication interaction flags
        │
        ▼
XGBoost Classifier
  - Binary: spike (glucose > threshold) within 2 hours
  - Output: probability [0, 1] + calibrated confidence interval
        │
        ▼
SHAP Explainer
  - Per-prediction feature contributions
  - Top-3 risk drivers returned alongside probability
        │
        ▼
Response: { spikeProbability, confidenceInterval, topRiskDrivers, modelVersion }
```

- Model artefacts stored in S3 (prod) or `ml-service/models/` (local).
- Model version is always recorded with each prediction (for audit).
- Retraining is an offline process; serving model is swapped via versioned artefact.

---

## AI Agent Architecture (LangGraph)

```
User query → Backend → ML service /agents endpoint
                              │
                    ┌─────────▼────────────┐
                    │  LangGraph Agent Graph│
                    │                      │
                    │  [Router node]        │
                    │      │               │
                    │      ├── RAG retriever tool
                    │      ├── EHR lookup tool (MCP)
                    │      ├── Prediction tool (MCP)
                    │      ├── Simulation tool (MCP)
                    │      └── Explanation tool
                    │                      │
                    │  [Synthesis node]     │
                    │  GPT-4o LLM call      │
                    │                      │
                    └─────────┬────────────┘
                              │
                    Structured response (explanation + citations)
```

- Agents are **stateless per request**; patient context is loaded from the Digital Twin on each call.
- All LLM-generated text is post-processed to strip any diagnostic or prescriptive language before returning to the caller.
- Agent memory (short-term within a session) is stored in Redis; not persisted beyond the session.

---

## MCP Architecture

The ML service exposes a **Model Context Protocol server** that defines tools callable by AI agents or external MCP-compatible clients.

```
MCP Server (ml-service/mcp/)
  ├── tool: get_patient_twin_state(patientId) → DigitalTwinState
  ├── tool: run_prediction(twinStateSnapshot) → PredictionResult
  ├── tool: run_simulation(twinStateSnapshot, scenario) → SimulationResult
  ├── tool: retrieve_guidelines(query) → RagResult[]
  └── tool: get_explanation(predictionId) → ExplanationResult
```

- Each MCP tool validates its inputs with Pydantic before execution.
- Tools are read-only or simulation-only; no MCP tool may write to the live patient state.
- MCP server runs on a separate internal port; not exposed to the public internet.

---

## RAG Architecture

```
Offline pipeline (indexing):
  Clinical guidelines (PDF/text) → chunking → OpenAI embeddings → pgvector store

Online pipeline (retrieval):
  Agent query → embed query → pgvector similarity search (top-k)
              → retrieved chunks → LLM context window → grounded response
```

- Source documents: clinical guidelines (ADA, WHO), published literature (synthetic/open-access only).
- Chunk size: 512 tokens with 64-token overlap.
- Retrieval: cosine similarity, top-5 chunks.
- Citations: retrieved chunks must be included in the agent response; the UI renders them as expandable sources.

---

## What-If Simulation Architecture

```
Doctor inputs scenario:
  { parameter: "stepCount", delta: +3000, timeHorizon: "24h" }
        │
        ▼
Backend: POST /api/v1/simulations → ML service POST /simulate
        │
        ▼
ML service:
  1. Load current DigitalTwinState (read-only)
  2. Clone state → apply mutation(s)
  3. Run prediction on mutated state
  4. Return SimulationResult (labelled SIMULATED, never OBSERVED)
        │
        ▼
Backend: persist SimulationResult (separate table, not mixed with real predictions)
        │
        ▼
Dashboard: renders side-by-side: current vs simulated prediction, with delta
```

**Constraint:** Simulations are always clearly labelled `SIMULATED` at every layer — in the database, in the API response, and in the UI.

---

## Security Boundaries

```
Internet
    │
    ▼
AWS API Gateway (TLS termination, rate limiting, WAF)
    │
    ▼
Backend API (authenticated endpoints, JWT validation)
    │  ┌──────────────────────────────────────┐
    │  │  Private VPC subnet                  │
    ▼  ▼                                      │
ML Service   Redis   PostgreSQL               │
(internal only, no direct internet access)    │
                                              │
S3 (model artefacts, IAM role access only) ───┘
```

- Backend is the **only** service with a public-facing endpoint.
- ML service, Redis, and PostgreSQL are in private subnets; reachable only from within the VPC.
- All inter-service traffic within VPC is over TLS or private network (no plaintext credentials in transit).
- See `security.md` for full security controls.
