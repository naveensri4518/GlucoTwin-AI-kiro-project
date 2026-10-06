# 🧬 GlucoTwin AI

### AI-Powered Digital Twin for Type 2 Diabetes

> An intelligent Digital Twin platform that models evolving patient health state, predicts near-term glucose spikes, explains risk factors, enables what-if simulations, and provides evidence-grounded clinical decision support.

---

## 🚀 Overview

**GlucoTwin AI** is a software-only Digital Twin platform designed for Type 2 Diabetes management and clinical decision support.

The platform combines:

- Historical patient/EHR information
- Dynamic wearable health signals
- Glucose time-series data
- Machine learning predictions
- Explainable risk factors
- What-if simulations
- Clinical knowledge retrieval
- AI-assisted explanations
- Agent-based orchestration
- Model Context Protocol (MCP)

The Digital Twin continuously represents the patient's evolving state and uses the available evidence to estimate the probability of a glucose spike within the next **2 hours**.

> ⚠️ **Safety:** GlucoTwin is a clinical decision-support prototype and is **not a diagnostic system or medical recommendation tool**.

---

# ✨ Key Features

## 🧬 Patient Digital Twin

Creates an evolving representation of a patient's health state using:

- Glucose readings
- Heart rate
- HRV
- Sleep duration
- Sleep stage
- Step count
- Activity level
- Historical EHR information

Each state is versioned to maintain the evolution of the Digital Twin.

---

## 📈 Glucose Spike Prediction

Predicts the probability of a glucose spike within the next **2 hours**.

The prediction dashboard provides:

- Spike probability
- Risk category
- Confidence interval
- Prediction horizon
- Model version
- Digital Twin version
- Data quality warnings
- Contributing factors

Prediction provenance is explicitly labelled as:

`PREDICTED`

---

## 🔍 Explainable Risk Analysis

GlucoTwin provides interpretable contributing factors instead of presenting only a prediction score.

Examples include:

- Recent glucose trend
- Physical activity
- Sleep duration
- Glucose variability
- Other available patient signals

---

## 🧪 What-If Simulation

Users can explore hypothetical scenarios without modifying the real Digital Twin.

Example inputs:

- Carbohydrate intake
- Activity level
- Medication taken

The system produces a separate simulated result containing:

- Simulated spike probability
- Risk category
- Confidence interval
- Change from baseline
- Scenario inputs

Simulation results are explicitly labelled:

`SIMULATED`

Real patient state is never overwritten.

---

## 🧠 Clinical Insight Pipeline

GlucoTwin combines multiple analysis stages to produce a structured clinical insight.

### Pipeline

```text
Twin Analysis
      ↓
Prediction Analysis
      ↓
Risk Evidence
      ↓
AI Explanation
      ↓
Verification
      ↓
Insight Assembly
      ↓
Audit
