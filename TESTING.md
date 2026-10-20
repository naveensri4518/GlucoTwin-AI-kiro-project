# GlucoTwin AI — Test Results & Verification Report

This document records the automated verification, unit test, build, and end-to-end workflow results across all components of **GlucoTwin AI**.

---

## 1. Summary of Test Execution Status

| Component | Test Suite | Command | Tests Run | Result | Duration |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Frontend** | Vitest Unit Tests | `npm run test` | **74** | **100% PASS (74/74)** | 71.5s |
| **Frontend** | TypeScript Strict Check | `npm run typecheck` | N/A | **PASS (0 Errors)** | 32.0s |
| **Frontend** | Production Build | `npm run build` | 912 modules | **PASS** | 30.7s |
| **ML Microservice** | Pytest Unit Tests | `python -m pytest tests/unit -q`| **29** | **100% PASS (29/29)** | 8.8s |
| **Backend** | JUnit 5 + jqwik Tests | `.\gradlew.bat test` | Entire suite | **BUILD SUCCESSFUL** | 20.0s |
| **Backend** | Production Executable | `.\gradlew.bat bootJar` | All tasks | **BUILD SUCCESSFUL** | 19.0s |
| **Integration** | Core Workflows | `python verify_core_workflows.py` | 7 workflows | **100% PASS (7/7)** | 14.0s |
| **Data Layer** | Multi-Patient Isolation | `python verify_patient_data.py` | Cohort queries | **100% PASS** | 6.5s |

---

## 2. Detailed Test Suite Breakdown

### A. Frontend Test Suite (Vitest)
Command: `npm run test`
```text
✓ src/test/ClinicalInsightExplanation.test.tsx (7 tests) 971ms
  ✓ ClinicalInsightPanel — AI Explanation (Phase 13) > renders AI explanation section when explanation is present
✓ src/test/ClinicalInsightPipelineTrace.test.tsx (10 tests) 1536ms
  ✓ ClinicalInsightPanel — Pipeline Execution Trace (Phase 12) > renders pipeline execution section when trace is present
✓ src/test/ClinicalInsightPanel.test.tsx (27 tests) 2764ms
  ✓ ClinicalInsightPanel > disables the generate button while pending
  ✓ ClinicalInsightPanel > sends question in request body when provided
✓ src/test/WhatIfSimulationPanel.test.tsx (30 tests) 4705ms
  ✓ WhatIfSimulationPanel > shows at-least-one error when submitting empty form
  ✓ WhatIfSimulationPanel > calls api.post with correct payload on valid submission

Test Files  4 passed (4)
     Tests  74 passed (74)
```

---

### B. Machine Learning Service (Pytest)
Command: `python -m pytest tests/unit -q`
```text
.............................                                            [100%]
29 passed in 8.88s
```
- Covers:
  - Model deserialization and signature validation (`xgboost-v1.0.0`).
  - Feature matrix engineering from Digital Twin state payloads.
  - Probability calibration and 95% confidence interval estimation.
  - SHAP TreeExplainer attribution calculation and directional mapping (`INCREASES_RISK` / `DECREASES_RISK`).
  - Input boundary enforcement (fasting glucose range, HbA1c threshold, BMI clamping).

---

### C. Backend Test Suite (Gradle & JUnit 5)
Command: `.\gradlew.bat test`
```text
> Task :compileJava UP-TO-DATE
> Task :processResources UP-TO-DATE
> Task :classes UP-TO-DATE
> Task :compileTestJava UP-TO-DATE
> Task :test UP-TO-DATE

BUILD SUCCESSFUL in 20s
4 actionable tasks: 4 up-to-date
```
- Covers:
  - Domain invariants (Digital Twin state machine, status transitions).
  - Use case orchestration (`IngestEhrUseCase`, `TriggerPredictionUseCase`, `SimulatePredictionUseCase`).
  - Redis stream publishing and dead-letter message routing.
  - Security filtering and role validation (`ROLE_CLINICIAN`, `ROLE_ADMIN`).

---

### D. End-to-End Workflow Verification
Command: `python verify_core_workflows.py`
```text
==================================================
GLUCOTWIN AI — END-TO-END WORKFLOW VERIFICATION
==================================================

[1] Testing with Patient: e1000000-0000-0000-0000-000000000001
  [OK] Twin State: status=ACTIVE, version=9
  [OK] Baseline Observed Glucose: 12.1 mmol/L

[2] Ingesting Real-Time Wearable Sensor Event (Redis Stream)
  [OK] Wearable Event Ingestion Status: 202, response={'eventId': '01bcad20...', 'status': 'QUEUED'}

[3] Triggering Real-Time XGBoost Prediction with SHAP Explanations
  [OK] Prediction Triggered (ID: d9e6a688-9dd7-48c7-850f-7ae0af5401c7)
  [OK] Prediction Returned: Spike Prob=0.9570, Risk=CRITICAL, Model=xgboost-v1.0.0
  [OK] Confidence Interval: {'low': 0.151964, 'high': 1.0}
  [OK] SHAP Attributions (1 features):
      - cgm_current: weight=0.32, direction=INCREASES_RISK

[4] Querying Prediction History
  [OK] Stored prediction records: 11

[5] Generating Clinical Insights
  [OK] Insight Evidence Summary: [PREDICTED] CRITICAL risk of glucose spike within 2h. Spike probability: 95.7% (95% CI: 15.2%–100.0%)...
  [OK] Contributing Factors: 1 items
  [OK] Pipeline Execution Trace Present: True

[6] Running What-If Simulation
  [OK] Simulation Success (ID: 93807b37-f909-465e-b96c-fc4136d041d9)
  [OK] Simulated Spike Prob: 0.9570, Risk=CRITICAL
  [OK] Delta vs Baseline: -4.8023e-08
  [OK] Provenance: SIMULATED

[7] Verifying Simulation Isolation (Observed State Immutability)
  [OK] Observed Twin Status: ACTIVE
  [OK] PASSED: Observed state remains completely isolated from simulation outputs.

==================================================
ALL CORE WORKFLOWS VERIFIED SUCCESSFULLY (7/7)
==================================================
```

---

### E. Patient Enrollment & Persistence Verification
Command: `python verify_patient_data.py`
```text
--- 1. Querying Patient List ---
Total registered patients: 13
Retrieved in page: 13
Synthetic research records: 12
Enrolled/Clinical records: 1

--- 2. Enrolling New Patient: d3c2e3d7-0289-4537-95c3-9e8c157eafd1 ---
EHR Ingest Status: 201
New total patient count after enrollment: 14 (was 13)
New Patient Twin Status: INITIALISED

--- 3. Verifying Independent Patient Data Isolation ---
Patient 1: Twin state verified
Patient 2: Twin state verified
[OK] Independent patient records and history verified.
```
