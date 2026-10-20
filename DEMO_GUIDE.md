# GlucoTwin AI — Evaluator Demonstration Guide

This guide provides a structured, step-by-step walkthrough to evaluate and demonstrate all capabilities of **GlucoTwin AI**.

---

## 🧭 Pre-Flight Checklist

Ensure all local microservices are active before beginning:
- **Frontend Dashboard:** [http://localhost:3000](http://localhost:3000)
- **Spring Boot Backend:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **FastAPI ML Service:** [http://localhost:8000/health](http://localhost:8000/health)
- **PostgreSQL:** Running on port `5433`
- **Redis:** Running on port `6379`

---

## 🎬 Demonstration Steps

### Step 1: Clinical Cohort Dashboard (`/dashboard`)
1. Navigate to **`http://localhost:3000/dashboard`**.
2. **Observe:**
   - **Registered Patient Count:** Displays actual total records stored in PostgreSQL (`14 Patients`).
   - **Digital Twin Health Breakdown:** Live count of `ACTIVE` vs `INITIALISED` vs `STALE` twins.
   - **Latest Cohort Glycemia:** Real average observed glucose.
   - **Recent Patient Table:** Shows enrolled patients with quick action shortcuts (`Predict`, `Workspace`).
   - **Design:** Notice the restrained Vercel-inspired dark theme, crisp typography, and absence of developer infrastructure clutter.

---

### Step 2: Patient Registry & Filter Tabs (`/patients`)
1. Click **Patients** in the left sidebar or navigate to **`/patients`**.
2. **Interact with Cohort Tabs:**
   - Click **All (14)**: Shows all registered records.
   - Click **Clinical (2)**: Filters to user-enrolled patient records.
   - Click **Synthetic (12)**: Filters to synthetic research cohort records.
3. **Test Search:**
   - In the search bar, type `e1000` to quickly filter by synthetic patient identifiers.

---

### Step 3: Enroll a New Real Patient (Live Database Ingestion)
1. On the **Patients** page, click the **+ Enroll Real Patient** button (top right).
2. The enrollment modal appears with realistic default clinical values:
   - Date of Birth: `1984-06-15`
   - Biological Sex: `FEMALE`
   - BMI: `27.2`
   - Diabetes Onset Date: `2020-03-10`
   - HbA1c: `7.1%`
   - Fasting Glucose: `6.8 mmol/L`
3. Click **Submit & Ingest EHR**.
4. **Observe:** The record is validated on the client, persisted into PostgreSQL via `POST /api/v1/patients/{id}/ehr`, and the patient table immediately reloads showing the new `USER-ENROLLED` patient.

---

### Step 4: 2-Hour Spike Predictions & SHAP Explanations (`/predictions`)
1. Click **Predictions** in the sidebar or navigate to **`/predictions`**.
2. Notice the top-level **Patient Context Selector**. Select synthetic patient `e1000000-0000-0000-0000-000000000001`.
3. **Trigger Real-Time ML Inference:**
   - Click **Generate Real-Time Prediction**.
   - The frontend calls `POST /api/v1/patients/{id}/predictions`, invoking the pre-trained XGBoost pipeline in the FastAPI service.
4. **Inspect Forecast Outputs:**
   - **Risk Gauge:** Displays calibrated spike probability (e.g., `95.7% CRITICAL`).
   - **Confidence Intervals:** Evaluates 95% CI bounds.
   - **SHAP Feature Attributions:** Direct statistical rankings showing which physiological factors drove the prediction (e.g., `cgm_current ↑ +32.0% Increases Risk`).
   - **Prediction History Log:** Chronological table of previously evaluated predictions stored in the database.

---

### Step 5: Multi-Agent Clinical Insights (`/insights`)
1. Click **Clinical Insights** in the sidebar.
2. The active patient context (`e1000000-0000-0000-0000-000000000001`) is preserved.
3. Enter an optional clinical question (e.g. *"What are the key dietary risks for this patient?"*) and click **Generate Clinical Insight**.
4. **Observe:**
   - **Clinical Evidence Summary:** Synthesizes observed EHR baselines and ML predictions into a medical narrative.
   - **Contributing Signals:** Itemizes clinical risk factors.
   - **Multi-Agent Pipeline Trace:** Shows execution steps (`RiskEvidenceAgent`, `ClinicalKnowledgeAgent`, `InsightVerificationAgent`).

---

### Step 6: Physiological Digital Twin Workspace (`/twin`)
1. Click **Digital Twin** in the sidebar.
2. **Inspect the Dual-Layer State:**
   - **Twin Version & Status:** Displays version (e.g., `v9`), synchronization timestamp, and active status.
   - **Static EHR Layer:** Demographics, biological sex, BMI, and baseline HbA1c.
   - **Dynamic Wearable Layer:** Rolling CGM buffer depth, heart rate, autonomic HRV tone, and physical activity.
   - **Status Lexicon:** Explains clinical semantics of `ACTIVE`, `INITIALISED`, and `STALE` states.

---

### Step 7: Wearable Monitoring & Sensor Ingestion (`/wearables`)
1. Click **Wearable Monitoring** in the sidebar.
2. **Observe:**
   - Continuous Glucose Monitoring (CGM) line chart plotting chronological glucose observations.
   - Autonomic tone and vitals grid (HR, HRV, Sleep Duration, Step Count).
3. **Simulate Live Sensor Ingestion:**
   - In the right-hand panel (*Ingest Wearable Sensor Event*), adjust Glucose Reading to `11.8 mmol/L`, HR to `82 bpm`, and click **Stream Sensor Reading**.
   - **Result:** Event is dispatched to `POST /api/v1/patients/{id}/wearable-events`, queued into the Redis stream (`glucotwin:wearable-events`), and processed to update the patient's Digital Twin.

---

### Step 8: What-If Counterfactual Simulations & State Isolation (`/simulations`)
1. Click **Simulations** in the sidebar.
2. In the **Simulation Scenario Builder**:
   - Set **Meal Carbohydrates** to `75 g`.
   - Set **Activity Level** to `MODERATE (30-60 min)`.
   - Toggle **Medication Taken** to `Yes`.
3. Click **Run Scenario Simulation**.
4. **Observe:**
   - Displays simulated spike probability and **Delta vs Baseline**.
   - Explicitly tagged with the violet **`SIMULATED`** provenance badge.
5. **Verify State Isolation Guarantee:**
   - Navigate back to **Digital Twin (`/twin`)**.
   - Notice that the patient's observed glucose and twin parameters remain **completely unchanged**. The simulation was executed strictly in transactional memory.

---

### Step 9: Audit & Safety Provenance (`/audit`)
1. Click **Audit & Activity** in the sidebar.
2. **Observe:**
   - **Safety Provenance Checks:** Verification of observed state immutability, SHAP explainability enforcement, and dead-letter queue routing.
   - **Telemetry Heartbeats:** Live connection status for Spring Boot (Port 8080), XGBoost FastAPI (Port 8000), PostgreSQL (Port 5433), and Redis (Port 6379).

---

### Step 10: Model Configuration & Platform Settings (`/settings`)
1. Click **Settings** in the sidebar.
2. **Inspect:**
   - Read-only machine learning hyperparameters (XGBoost v1.0.0, 2.0-hour horizon, 10.0 mmol/L threshold).
   - Session authentication role (`ROLE_CLINICIAN`).
   - Rule-based deterministic insight fallback mode.
