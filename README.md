# GlucoTwin AI 🩺
> **Clinical Decision Support & Physiological Digital Twin Platform for Type 2 Diabetes**  
> **Submitted for: Unstop Digital Twin Challenge 2026**

---

## 👥 Team & Institution Details

- **Institution:** Karpagam Institute of Technology, Coimbatore, Tamil Nadu, India
- **Department:** Computer Science & Engineering / Information Technology
- **Repository:** [https://github.com/naveensri4518/GlucoTwin-AI-kiro-project](https://github.com/naveensri4518/GlucoTwin-AI-kiro-project)
- **Team Lead & Primary Developer:** **NAVEENSRI V** (Full-Stack Architecture, ML Pipeline & Digital Twin State Engine)
- **Contact:** naveensri4518@gmail.com
- **Team Members:** *(Collaborative multi-member submission — see submission form for finalized member roster)*

---

## 🎯 Problem Statement

Type 2 Diabetes affects over 500 million individuals globally and is a leading cause of cardiovascular disease, renal failure, and retinopathy. Effective glycemic management requires maintaining blood glucose within a narrow therapeutic target range (3.9–10.0 mmol/L / 70–180 mg/dL). 

However, current clinical management faces two fundamental bottlenecks:
1. **Snapshots vs. Continuity:** Conventional clinical reviews rely primarily on quarterly HbA1c tests, which provide retrospective, 90-day glycemic averages but conceal acute, dangerous postprandial glucose excursions and nighttime hypoglycemic dips.
2. **Data Deluge Without Actionable Foresight:** Continuous Glucose Monitors (CGM) stream measurements every 5 minutes, yet clinicians lack predictive clinical decision support tools capable of synthesizing continuous biometric telemetry with patient electronic health records (EHR) to forecast excursions before they occur.

---

## 🏥 Healthcare Use Case & Clinical Value

GlucoTwin AI solves this problem by maintaining an individual **Physiological Digital Twin** for each patient, providing:
1. **Early Glycemic Excursion Warnings:** Predicts the risk of blood glucose spiking above 10.0 mmol/L within a forward 2-hour window, giving clinicians and patients lead time for behavioral or therapeutic intervention.
2. **Explainable AI (XAI):** Provides exact SHAP (SHapley Additive exPlanations) statistical attributions explaining *why* a spike is forecast (e.g. rapid CGM ascent rate, autonomic stress, sleep deprivation).
3. **Counterfactual "What-If" Simulations:** Enables clinicians to model hypothetical interventions (e.g. reduced meal carbohydrates, moderate postprandial exercise, medication adherence) before changing patient care plans, with guaranteed memory isolation from real patient records.

---

## 🏗️ Technical Stack

| Tier | Technologies Used |
| :--- | :--- |
| **Frontend UI** | React 18, TypeScript, Vite 5, Tailwind CSS (Vercel-inspired dark theme), TanStack Query v5, Recharts |
| **Application Core** | Java 21, Spring Boot 3.3.0, Spring Data JPA, Spring Security (`ROLE_CLINICIAN`), Flyway, Spring AI |
| **Machine Learning** | Python 3.11+, FastAPI, Uvicorn, XGBoost 2.0.3, SHAP 0.44+, Scikit-learn, NumPy, Pydantic |
| **Event Bus & Streaming** | Redis 7 Streams (`glucotwin:wearable-events`), Consumer Groups, Dead-Letter Stream routing |
| **Database Tier** | PostgreSQL 16 (Port 5433), 12 versioned Flyway schema migrations, connection pooling |
| **Architecture Docs** | ReportLab (Vector PDF generation), Python-PPTX (Presentation decks) |

---

## 🤖 AI / ML Models & Framework Details

- **Model Architecture:** Calibrated **XGBoost (Extreme Gradient Boosting) v1.0.0** binary classifier.
- **Prediction Target:** Probability of postprandial hyperglycemia exceeding `10.0 mmol/L` (`180 mg/dL`) within a `2.0-hour` forward horizon.
- **Inference Pipeline:** Python FastAPI microservice (port 8000) listening on internal authenticated REST endpoints.
- **Model Explainability:** Integrated **SHAP TreeExplainer** evaluating feature attributions on every prediction request, yielding directional impact weights (e.g., `cgm_current ↑ +32.0% Increases Risk`).
- **Uncertainty Quantification:** Computes empirical **95% Confidence Intervals (CI)** for each risk estimate.
- **Categorical Risk Stratification:** Automatically stratifies probabilities into `LOW` (<30%), `MODERATE` (30–60%), `HIGH` (60–85%), and `CRITICAL` (≥85%).
- **Multi-Agent Clinical Insights:** Orchestrates four collaborative agents (`RiskEvidenceAgent`, `ClinicalKnowledgeAgent`, `InsightExplanationAgent`, `InsightVerificationAgent`) with deterministic rule-based fallback guarantees when external LLMs are offline.

---

## 📁 Key Submission Materials & Document Links

| Deliverable | Format | Repository File Link |
| :--- | :--- | :--- |
| **Architecture Diagram** | PDF (Vector) | [docs/submission/GLUCOTWIN_AI_ARCHITECTURE_DIAGRAM.pdf](docs/submission/GLUCOTWIN_AI_ARCHITECTURE_DIAGRAM.pdf) |
| **Project Presentation** | PDF (Slide Deck) | [docs/submission/GLUCOTWIN_AI_PRESENTATION.pdf](docs/submission/GLUCOTWIN_AI_PRESENTATION.pdf) |
| **Project Presentation** | PPTX (Editable) | [docs/submission/GLUCOTWIN_AI_PRESENTATION.pptx](docs/submission/GLUCOTWIN_AI_PRESENTATION.pptx) |
| **Demo Video Script & Guide** | Markdown | [docs/submission/DEMO_VIDEO_SCRIPT.md](docs/submission/DEMO_VIDEO_SCRIPT.md) |
| **System Architecture Guide** | Markdown | [ARCHITECTURE.md](ARCHITECTURE.md) |
| **Complete Testing Report** | Markdown | [TESTING.md](TESTING.md) |
| **Live Evaluator Demo Guide**| Markdown | [DEMO_GUIDE.md](DEMO_GUIDE.md) |
| **Open-Source License** | MIT | [LICENSE](LICENSE) |

---

## 🎥 Demonstration Video (15–20 Minutes)

- **Video Hosting:** YouTube (**UNLISTED**)
- **Demo Video URL:** *[ACTION REQUIRED: Upload your 15–20 min screen recording to YouTube as Unlisted and paste URL here]*
- **Video Recording Guide:** See [docs/submission/DEMO_VIDEO_SCRIPT.md](docs/submission/DEMO_VIDEO_SCRIPT.md) for the exact minute-by-minute speaking script, screen actions, and recording checklist.

---

## 📜 Open-Source License

GlucoTwin AI is licensed under the permissive **MIT License**.  
See the full text in [LICENSE](LICENSE).

---

## 🚀 Public Accessibility & Local Reproduction

The repository is hosted publicly at:  
👉 **[https://github.com/naveensri4518/GlucoTwin-AI-kiro-project](https://github.com/naveensri4518/GlucoTwin-AI-kiro-project)**

### Quick Start Instructions

#### 1. Clone & Start Containers
```bash
git clone https://github.com/naveensri4518/GlucoTwin-AI-kiro-project.git
cd GlucoTwin-AI-kiro-project
docker compose up -d postgres redis
```

#### 2. Start ML Microservice (Port 8000)
```powershell
cd ml-service
$env:MODEL_DIR = "models/xgboost-v1.0.0"
$env:ML_SERVICE_INTERNAL_TOKEN = "dev-internal-token"
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

#### 3. Start Spring Boot Core (Port 8080)
```powershell
cd backend
.\gradlew.bat bootRun
```

#### 4. Start React Frontend (Port 3000)
```powershell
cd frontend
npm install
npm run dev
```

Access the dashboard at **`http://localhost:3000`**.

---

## 📊 Automated Test Results & Verification

All test suites across the stack pass with 100% success:
- **Frontend Unit Tests (Vitest):** **74 / 74 tests passing** (`npm run test`)
- **Frontend Typecheck (tsc):** **0 errors** (`npm run typecheck`)
- **Frontend Production Build:** **912 modules compiled** (`npm run build`)
- **Python ML Unit Tests (Pytest):** **29 / 29 tests passing** (`python -m pytest tests/unit -q`)
- **Backend Core Tests (Gradle):** **BUILD SUCCESSFUL** (`.\gradlew.bat test`)
- **Backend Executable JAR:** **BUILD SUCCESSFUL** (`.\gradlew.bat bootJar`)
- **End-to-End Workflow Verification:** **7 / 7 core workflows passed** (`python verify_core_workflows.py`)

See [TESTING.md](TESTING.md) for complete execution logs.

---

## ⚕️ Clinical Safety Disclaimer

**IMPORTANT RESEARCH & EVALUATION NOTICE:**  
GlucoTwin AI is developed strictly as a research prototype and clinical decision support system. It is not an autonomous medical device, does not formulate diagnostic claims, and does not prescribe medication or insulin dosages. All prognostic inferences and counterfactual simulations must be evaluated by licensed healthcare professionals.
