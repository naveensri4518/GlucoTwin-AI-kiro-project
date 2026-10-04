"""
Synthetic model training script.

Generates synthetic glucose spike training data, trains XGBoost, saves SHAP explainer
and conformal calibration scores. Run this once to produce model artefacts.

Usage:
    cd ml-service
    python scripts/train_synthetic_model.py

Output:
    models/xgboost-v1.0.0/model.joblib
    models/xgboost-v1.0.0/explainer.joblib
    models/xgboost-v1.0.0/calibration_scores.npy
    models/xgboost-v1.0.0/imputation_config.json
    models/xgboost-v1.0.0/metadata.json
"""
from __future__ import annotations

import json
import sys
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.calibration import CalibratedClassifierCV
from sklearn.metrics import roc_auc_score
from sklearn.model_selection import train_test_split
from xgboost import XGBClassifier

# Add parent to path
sys.path.insert(0, str(Path(__file__).parent.parent))

OUTPUT_DIR = Path(__file__).parent.parent / "models" / "xgboost-v1.0.0"
N_PATIENTS = 1000
EVENTS_PER_PATIENT = 100
GLUCOSE_SPIKE_THRESHOLD = 10.0  # mmol/L
RANDOM_SEED = 42
VERSION = "xgboost-v1.0.0"

rng = np.random.default_rng(RANDOM_SEED)


def generate_synthetic_data() -> pd.DataFrame:
    """Generate synthetic feature data with realistic glucose spike patterns."""
    rows = []
    for patient_idx in range(N_PATIENTS):
        # Patient-level characteristics
        base_glucose = rng.uniform(5.5, 9.5)
        base_hba1c = rng.uniform(6.5, 10.0)
        base_bmi = rng.uniform(22.0, 40.0)
        days_since_dx = rng.integers(365, 5475)  # 1-15 years

        for event_idx in range(EVENTS_PER_PATIENT):
            hour = rng.integers(0, 24)
            cgm_current = base_glucose + rng.normal(0, 0.8)
            cgm_delta_30m = rng.normal(0, 0.4)
            cgm_mean_60m = base_glucose + rng.normal(0, 0.5)
            cgm_slope_60m = rng.normal(0, 0.05)

            heart_rate = rng.uniform(55, 100)
            hrv = rng.uniform(20, 80)
            sleep_duration = rng.uniform(4, 9)
            step_count = rng.integers(500, 15000)
            activity_enc = rng.integers(0, 4)

            # Risk factors increase spike probability - stronger signal for better AUC
            spike_risk = -2.0  # base offset (negative = low base rate)
            # CGM current level is the dominant predictor
            spike_risk += (cgm_current - 7.0) * 0.8
            # Rising trend strongly increases risk
            spike_risk += cgm_delta_30m * 1.5
            # Slope adds additional signal
            spike_risk += cgm_slope_60m * 20.0
            # HbA1c baseline risk
            spike_risk += (base_hba1c - 7.0) * 0.4
            # High BMI adds modest risk
            spike_risk += (base_bmi - 25.0) * 0.05
            # Low activity increases risk
            if activity_enc == 0:  # SEDENTARY
                spike_risk += 0.5
            # Low step count
            if step_count < 2000:
                spike_risk += 0.3
            # Nocturnal effect
            if hour < 6 or hour >= 22:
                spike_risk += 0.4
            # Low HRV (poor autonomic tone)
            if hrv < 30:
                spike_risk += 0.4

            # Add controlled noise
            spike_risk += rng.normal(0, 0.3)
            spike_prob = 1 / (1 + np.exp(-spike_risk))
            label = int(rng.random() < spike_prob)

            rows.append({
                "cgm_current": float(np.clip(cgm_current, 1.0, 35.0)),
                "cgm_delta_30m": float(cgm_delta_30m),
                "cgm_mean_60m": float(np.clip(cgm_mean_60m, 1.0, 35.0)),
                "cgm_slope_60m": float(cgm_slope_60m),
                "heart_rate_current": float(heart_rate),
                "hrv_current": float(hrv),
                "sleep_duration_last": float(sleep_duration),
                "step_count_today": float(step_count),
                "activity_level_enc": float(activity_enc),
                "hba1c_latest": float(base_hba1c),
                "bmi": float(base_bmi),
                "time_of_day_sin": float(np.sin(2 * np.pi * hour / 24)),
                "time_of_day_cos": float(np.cos(2 * np.pi * hour / 24)),
                "days_since_diagnosis": float(days_since_dx),
                "label": label,
            })

    return pd.DataFrame(rows)


FEATURE_COLS = [
    "cgm_current", "cgm_delta_30m", "cgm_mean_60m", "cgm_slope_60m",
    "heart_rate_current", "hrv_current", "sleep_duration_last", "step_count_today",
    "activity_level_enc", "hba1c_latest", "bmi",
    "time_of_day_sin", "time_of_day_cos", "days_since_diagnosis",
]


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"Generating synthetic training data ({N_PATIENTS} patients - {EVENTS_PER_PATIENT} events)...")

    df = generate_synthetic_data()
    X = df[FEATURE_COLS].values
    y = df["label"].values

    print(f"Dataset: {len(df)} samples, {y.mean():.2%} positive (spike) rate")

    # Train / val / test split: 60/20/20
    X_train, X_temp, y_train, y_temp = train_test_split(X, y, test_size=0.40, random_state=RANDOM_SEED, stratify=y)
    X_val, X_test, y_val, y_test = train_test_split(X_temp, y_temp, test_size=0.50, random_state=RANDOM_SEED, stratify=y_temp)

    print(f"Train: {len(X_train)}, Val: {len(X_val)}, Test: {len(X_test)}")

    # Train XGBoost
    print("Training XGBoost classifier...")
    model = XGBClassifier(
        n_estimators=200,
        max_depth=6,
        learning_rate=0.1,
        subsample=0.8,
        colsample_bytree=0.8,
        eval_metric="logloss",
        use_label_encoder=False,
        random_state=RANDOM_SEED,
        n_jobs=-1,
    )
    model.fit(X_train, y_train, eval_set=[(X_val, y_val)], verbose=False)

    # Evaluate on test set
    y_pred_proba = model.predict_proba(X_test)[:, 1]
    auc = roc_auc_score(y_test, y_pred_proba)
    print(f"Test AUC-ROC: {auc:.4f}")
    if auc < 0.80:
        print(f"WARNING: AUC {auc:.4f} < 0.80 threshold - model may need more data or tuning")

    # Save model
    model_path = OUTPUT_DIR / "model.joblib"
    joblib.dump(model, model_path)
    print(f"Saved model - {model_path}")

    # SHAP explainer on training set
    print("Training SHAP TreeExplainer...")
    try:
        import shap
        explainer = shap.TreeExplainer(model)
        explainer_path = OUTPUT_DIR / "explainer.joblib"
        joblib.dump(explainer, explainer_path)
        print(f"Saved SHAP explainer - {explainer_path}")
    except Exception as e:
        print(f"WARNING: Failed to create SHAP explainer: {e}")

    # Conformal calibration scores on validation set
    print("Computing conformal calibration scores...")
    val_proba = model.predict_proba(X_val)[:, 1]
    # Nonconformity score: how wrong is the prediction?
    calibration_scores = np.abs(y_val.astype(float) - val_proba)
    cal_path = OUTPUT_DIR / "calibration_scores.npy"
    np.save(cal_path, calibration_scores)
    print(f"Saved calibration scores ({len(calibration_scores)} samples) - {cal_path}")

    # Imputation config (population medians from training data)
    df_train = df.iloc[:len(X_train)]
    imputation_config = {
        "heart_rate_median": float(df_train["heart_rate_current"].median()),
        "hrv_median": float(df_train["hrv_current"].median()),
        "sleep_median": float(df_train["sleep_duration_last"].median()),
        "step_median": float(df_train["step_count_today"].median()),
        "hba1c_median": float(df_train["hba1c_latest"].median()),
        "bmi_median": float(df_train["bmi"].median()),
        "cgm_delta_30m_median": float(df_train["cgm_delta_30m"].median()),
        "cgm_mean_60m_median": float(df_train["cgm_mean_60m"].median()),
        "cgm_slope_60m_median": float(df_train["cgm_slope_60m"].median()),
        "activity_enc_median": float(df_train["activity_level_enc"].median()),
        "days_since_diagnosis_median": float(df_train["days_since_diagnosis"].median()),
    }
    imp_path = OUTPUT_DIR / "imputation_config.json"
    with open(imp_path, "w") as f:
        json.dump(imputation_config, f, indent=2)
    print(f"Saved imputation config - {imp_path}")

    # Metadata
    metadata = {
        "version": VERSION,
        "type": "xgboost",
        "trainedAt": datetime.now(timezone.utc).isoformat(),
        "featureOrder": FEATURE_COLS,
        "glucoseSpikeThreshold": GLUCOSE_SPIKE_THRESHOLD,
        "trainingSamples": int(len(X_train)),
        "testAucRoc": float(auc),
    }
    meta_path = OUTPUT_DIR / "metadata.json"
    with open(meta_path, "w") as f:
        json.dump(metadata, f, indent=2)
    print(f"Saved metadata - {meta_path}")
    print("\nTraining complete!")
    print(f"  Version:   {VERSION}")
    print(f"  AUC-ROC:   {auc:.4f}")
    print(f"  Artefacts: {OUTPUT_DIR}/")


if __name__ == "__main__":
    main()
