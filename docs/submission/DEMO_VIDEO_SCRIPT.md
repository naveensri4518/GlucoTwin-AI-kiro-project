# GlucoTwin AI — 15–20 Minute Video Demonstration Script & Recording Guide
> **Unstop Digital Twin Challenge 2026 Submission Deliverable**

This document provides the exact minute-by-minute speaking script, screen setup, workflow demonstrations, and checklist for recording the required 15–20 minute unlisted YouTube demonstration video.

---

## 📋 Pre-Recording Setup Checklist

- [ ] **Display Resolution:** 1920x1080 (1080p), 16:9 ratio.
- [ ] **Microphone:** Clear audio input, minimal background noise.
- [ ] **Browser Window:** Chrome/Edge open at `http://localhost:3000/dashboard` in full-screen or clean window.
- [ ] **Terminal Windows Ready:**
  1. Docker containers running (`docker compose ps` showing Postgres on 5433 and Redis on 6379).
  2. Spring Boot running on port 8080 (`.\gradlew.bat bootRun`).
  3. Python FastAPI ML service running on port 8000 (`uvicorn main:app`).
  4. Frontend running on port 3000 (`npm run dev`).
- [ ] **Video Hosting Requirement:** **YouTube (UNLISTED)**. Do NOT set to Private (judges cannot view private videos) or Public.

---

## ⏱️ Minute-by-Minute Demonstration Script (18 Minutes Total)

### Part 1: Introduction & Clinical Problem Statement (00:00 – 02:30)
- **Visual:** Slide 1 & 2 of `GLUCOTWIN_AI_PRESENTATION.pdf` or opening title screen.
- **Presenter Speaking Points:**
  - *"Hello evaluators and judges of the Unstop Digital Twin Challenge 2026. We are presenting GlucoTwin AI, an AI-powered Physiological Digital Twin platform for Type 2 Diabetes clinical decision support, developed at Karpagam Institute of Technology, Coimbatore, Tamil Nadu, India."*
  - *"Type 2 Diabetes affects over 500 million people globally. A major clinical challenge is that quarterly HbA1c tests only offer retrospective, aggregate glucose metrics. Meanwhile, modern Continuous Glucose Monitors (CGM) stream massive volumes of telemetry, but clinicians lack forward-looking predictive tools to anticipate postprandial glucose spikes before they occur."*
  - *"GlucoTwin AI bridges this gap by creating an individual, versioned Physiological Digital Twin for each patient, delivering 2-hour forward-looking spike predictions, SHAP explainability, and counterfactual what-if simulations."*

---

### Part 2: System Architecture & Technology Stack (02:30 – 05:00)
- **Visual:** Display `GLUCOTWIN_AI_ARCHITECTURE_DIAGRAM.pdf` on screen.
- **Presenter Speaking Points:**
  - *"Our system is built on a decoupled, reactive microservice architecture across five primary tiers:"*
  - *"1. Presentation Layer: React 18 single-page application with TypeScript, Vite, and a custom Vercel-inspired dark theme for high clinical contrast."*
  - *"2. Application Core: Java 21 and Spring Boot 3.3.0 REST API managing the dual-layer Digital Twin state machine and orchestrating multi-agent clinical insights."*
  - *"3. Streaming Bus: Redis 7 Streams ingesting continuous CGM, heart rate, and autonomic vitals."*
  - *"4. Machine Learning Tier: A dedicated Python FastAPI microservice running an XGBoost binary classifier paired with a SHAP TreeExplainer for instant feature attributions."*
  - *"5. Persistence Layer: PostgreSQL 16 with versioned Flyway migrations storing patient EHR, Digital Twin states, and immutable audit trails."*

---

### Part 3: Clinical Cohort Dashboard Overview (05:00 – 07:00)
- **Visual:** Switch to browser at `http://localhost:3000/dashboard`.
- **Presenter Actions & Speaking Points:**
  - *"Here is the GlucoTwin AI live clinician dashboard."*
  - Point to the cohort metric cards:
    - *"Notice our live cohort overview: 15 registered patients persisted in PostgreSQL, broken down by Digital Twin health (Active vs. Initialised vs. Stale)."*
    - *"Average cohort glucose and spike risks are calculated live from real database queries."*
    - *"Notice our restrained, professional black-and-silver theme: no developer clutter or distracting neon effects—pure clinical focus."*
  - Point out the recent patient directory table and show navigation shortcuts.

---

### Part 4: Patient Registry & Live Patient Enrollment (07:00 – 09:30)
- **Visual:** Navigate to `/patients`.
- **Presenter Actions & Speaking Points:**
  - *"Navigating to the Patients Registry, we see all 15 patient records independently stored in PostgreSQL."*
  - Click on the filter tabs: **All (15)**, **Clinical (3)**, and **Synthetic (12)**:
    - *"We maintain a strict data provenance standard: synthetic research patients are clearly identified, while user-enrolled clinical patients are tracked separately."*
  - Click **+ Enroll Real Patient**:
    - Fill in demographic and metabolic values: DOB, Biological Sex, BMI (26.8), Diabetes Onset Date, HbA1c (7.2%), Fasting Glucose (6.5 mmol/L).
    - Click **Submit & Ingest EHR**.
    - Show the immediate creation of the patient and their `INITIALISED` Digital Twin in PostgreSQL.

---

### Part 5: 2-Hour Spike Predictions & SHAP Explainability (09:30 – 12:00)
- **Visual:** Navigate to `/predictions`.
- **Presenter Actions & Speaking Points:**
  - Select patient `e1000000-0000-0000-0000-000000000001` using the **Patient Context Selector**.
  - Click **Generate Real-Time Prediction**:
    - *"When we trigger a prediction, our backend packages the patient's active twin state and calls the XGBoost microservice on port 8000."*
    - Show the risk gauge displaying probability (e.g. `95.7% CRITICAL`), 95% confidence intervals, and model version lock (`xgboost-v1.0.0`).
    - Scroll to **SHAP Feature Attributions**:
      - *"Crucially for clinical safety, our model is not a black box. The SHAP TreeExplainer breaks down feature attribution: current CGM level (+32.0%) and high glycemic volatility drive the predicted spike."*
    - Show the historical predictions table stored in the database.

---

### Part 6: Multi-Agent Clinical Insights & Evidence Trace (12:00 – 14:00)
- **Visual:** Navigate to `/insights`.
- **Presenter Actions & Speaking Points:**
  - Keep the active patient context selected.
  - Enter a clinician question: *"What dietary and activity precautions are recommended?"*
  - Click **Generate Clinical Insight**:
    - *"Our backend coordinates four domain agents: RiskEvidenceAgent, ClinicalKnowledgeAgent, InsightExplanationAgent, and InsightVerificationAgent."*
    - Show the synthesized evidence summary, contributing factors, and inspect the expandable **Pipeline Execution Trace** proving agent verification before presentation.

---

### Part 7: Continuous Wearable Monitoring & Sensor Ingestion (14:00 – 15:30)
- **Visual:** Navigate to `/wearables`.
- **Presenter Actions & Speaking Points:**
  - Point to the continuous glucose monitoring (CGM) line chart and vitals grid (heart rate, HRV, sleep, step count).
  - In the right-hand panel, adjust the sensor reading to `11.5 mmol/L` and click **Stream Sensor Reading**:
    - *"This dispatches a real-time event to the Redis stream (glucotwin:wearable-events), updating the patient's rolling CGM buffer and advancing the Digital Twin version."*

---

### Part 8: What-If Counterfactual Simulations & Memory Isolation (15:30 – 17:00)
- **Visual:** Navigate to `/simulations`.
- **Presenter Actions & Speaking Points:**
  - In the scenario builder, set Meal Carbohydrates to `75g`, Activity Level to `MODERATE`, and Medication to `Yes`.
  - Click **Run Scenario Simulation**:
    - Show the simulated spike trajectory, Delta vs. Baseline, and the violet **SIMULATED** badge.
  - Navigate back to `/twin` to verify:
    - *"Notice that the patient's observed Digital Twin remains completely unchanged. Simulations run in isolated transactional memory and are strictly forbidden from modifying observed EHR data."*

---

### Part 9: Audit, Testing, and Conclusion (17:00 – 18:30)
- **Visual:** Navigate to `/audit`, then show terminal testing results (`npm run test`, `pytest`, `gradlew test`).
- **Presenter Speaking Points:**
  - *"Our Audit & Activity view monitors data provenance rules and live microservice heartbeats across Spring Boot, FastAPI, PostgreSQL, and Redis."*
  - *"All automated test suites pass with 100% success: 74 frontend Vitest tests, 29 Python pytest unit tests, and all backend JUnit tests."*
  - *"In conclusion, GlucoTwin AI demonstrates how modern Physiological Digital Twins, real-time event streaming, and explainable AI can transform Type 2 Diabetes care. Thank you!"*

---

## 📤 Submission Upload Instructions (Action for Submitter)

1. **Record the Video:** Use OBS Studio, Zoom, or Loom to record following the script above.
2. **Export:** Export as 1080p MP4.
3. **Upload to YouTube:**
   - Title: `GlucoTwin AI — Unstop Digital Twin Challenge 2026 Demo`
   - Privacy: **UNLISTED** (Anyone with the link can view; not public, not private).
4. **Copy Link:** Paste your unlisted YouTube URL into the Unstop submission form and update the Demo Video section in `README.md`.
