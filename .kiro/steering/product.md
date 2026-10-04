---
inclusion: always
---

# GlucoTwin AI — Product Steering

## Product Purpose

GlucoTwin AI is a software-only AI-powered Digital Twin for Type 2 Diabetes. It gives clinicians a continuously updated virtual model of a patient that fuses historical EHR data with simulated real-time wearable/IoT streams. The system predicts the probability of a glucose spike within the next 2 hours, provides explainable risk analysis, and supports what-if scenario simulation.

This is a **research and prototype decision-support tool**, not a diagnostic or prescriptive system.

---

## Problem Statement

Clinicians managing Type 2 Diabetes patients lack a unified, real-time view that connects historical records with continuous physiological signals. Existing tools are either retrospective (EHR viewers) or reactive (alert-only CGM dashboards). There is no layer that synthesises both, maintains a live patient state, explains risk drivers, and lets a doctor explore counterfactual futures ("what if the patient adds an evening walk?").

GlucoTwin AI fills that gap.

---

## Target Users

- **Primary:** Endocrinologists and diabetologists making day-to-day treatment decisions.
- **Secondary:** Clinical data scientists validating digital twin models.
- **Out of scope:** Patients accessing their own data directly; general practitioners without specialist context.

---

## Core Capabilities

| Capability | Description |
|---|---|
| Digital Twin state | Continuously updated virtual patient model combining static EHR and time-series wearable data |
| Glucose spike prediction | Probabilistic forecast (0–100 %) for a spike event within the next 2 hours |
| Explainable risk analysis | Feature-importance and natural-language explanations of risk drivers |
| What-if simulation | Doctor-driven scenario exploration (diet, exercise, medication adjustments) |
| EHR integration | Ingestion of structured patient records (demographics, diagnoses, medications, labs) |
| Wearable/IoT ingestion | Simulated real-time streams: CGM, activity, heart rate, sleep |
| AI agent orchestration | LangGraph agents that route queries, call tools, and synthesise context |
| RAG knowledge layer | Retrieval-augmented generation over clinical guidelines and literature |

---

## Digital Twin Concept

The Digital Twin is a **persistent, evolving virtual state object** per patient. It is not a static snapshot.

```
Patient State = f(EHR history, wearable stream, time, prior predictions)
```

- **Static layer:** Demographics, diagnosis codes, medication history, lab results — ingested from EHR, changes infrequently.
- **Dynamic layer:** CGM readings, activity, HR, sleep — updated on each wearable event.
- **Prediction layer:** ML model that reads the current state and outputs a spike probability + confidence interval.
- **Simulation layer:** A copy of the current state with hypothetical mutations applied; prediction runs on the mutated copy.

---

## Safety Boundaries

These rules are **non-negotiable** across every layer of the system.

1. **No diagnostic claims.** The system must never state a diagnosis. All outputs are predictions with stated uncertainty.
2. **No prescriptive advice.** The system must never recommend a specific medication dose or treatment.
3. **No guarantee of outcomes.** All predictions are probabilistic estimates, not medical guarantees.
4. **Data labelling.** Every data item displayed to the user must be labelled as one of: `OBSERVED`, `PREDICTED`, or `SIMULATED`. Mixing unlabelled data is a defect.
5. **Synthetic/anonymised data only.** No real patient PII may be used in development, testing, or demos. See `security.md`.
6. **Prominent disclaimer.** The UI must display a persistent, visible disclaimer: *"For research and decision support only. Not a diagnostic system. Always apply clinical judgment."*

---

## Success Criteria

- A doctor can load a patient, view current Digital Twin state, and see a glucose-spike probability with an explanation in ≤ 3 seconds (p95).
- What-if simulation returns results in ≤ 5 seconds (p95).
- Spike prediction achieves AUC-ROC ≥ 0.80 on held-out synthetic validation data.
- All displayed data items are correctly labelled (`OBSERVED` / `PREDICTED` / `SIMULATED`).
- Zero instances of diagnostic or prescriptive language in any UI copy, API response, or AI-generated explanation.
- System passes all safety boundary checks in automated test suite.
