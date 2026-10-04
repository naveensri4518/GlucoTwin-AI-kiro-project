"""
Model validation script — runs against held-out synthetic test data.
CI fails if any threshold is not met.

Usage:
    cd ml-service
    python scripts/validate_model.py [--model-dir models/xgboost-v1.0.0]
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import joblib
import numpy as np
from sklearn.metrics import (
    roc_auc_score,
    precision_score,
    recall_score,
)

sys.path.insert(0, str(Path(__file__).parent.parent))

# Thresholds (configurable via CLI)
THRESHOLDS = {
    "auc_roc": 0.80,
    "precision": 0.70,
    "recall": 0.65,
    "ece": 0.10,
}


def compute_ece(y_true: np.ndarray, y_prob: np.ndarray, n_bins: int = 10) -> float:
    """Expected calibration error."""
    bins = np.linspace(0, 1, n_bins + 1)
    ece = 0.0
    for i in range(n_bins):
        mask = (y_prob >= bins[i]) & (y_prob < bins[i + 1])
        if mask.sum() == 0:
            continue
        avg_conf = y_prob[mask].mean()
        avg_acc = y_true[mask].mean()
        ece += mask.sum() / len(y_true) * abs(avg_conf - avg_acc)
    return float(ece)


def main(model_dir: Path) -> int:
    print(f"\n=== GlucoTwin Model Validation ===")
    print(f"Model directory: {model_dir}")

    model_path = model_dir / "model.joblib"
    if not model_path.exists():
        print(f"ERROR: Model not found at {model_path}")
        print("Run scripts/train_synthetic_model.py first.")
        return 1

    model = joblib.load(model_path)

    # Re-generate held-out test data with same seed
    from scripts.train_synthetic_model import generate_synthetic_data, FEATURE_COLS, RANDOM_SEED
    from sklearn.model_selection import train_test_split

    print("Generating held-out test data...")
    df = generate_synthetic_data()
    X = df[FEATURE_COLS].values
    y = df["label"].values

    _, X_temp, _, y_temp = train_test_split(X, y, test_size=0.40, random_state=RANDOM_SEED, stratify=y)
    _, X_test, _, y_test = train_test_split(X_temp, y_temp, test_size=0.50, random_state=RANDOM_SEED, stratify=y_temp)

    print(f"Test set: {len(X_test)} samples")

    y_prob = model.predict_proba(X_test)[:, 1]
    y_pred = (y_prob >= 0.5).astype(int)

    auc = roc_auc_score(y_test, y_prob)
    precision = precision_score(y_test, y_pred, zero_division=0)
    recall = recall_score(y_test, y_pred, zero_division=0)
    ece = compute_ece(y_test, y_prob)

    results = {
        "auc_roc": auc,
        "precision": precision,
        "recall": recall,
        "ece": ece,
    }

    print("\n=== Results ===")
    all_pass = True
    for metric, value in results.items():
        threshold = THRESHOLDS[metric]
        # ECE: lower is better
        if metric == "ece":
            passed = value <= threshold
        else:
            passed = value >= threshold
        status = "PASS" if passed else "FAIL"
        if not passed:
            all_pass = False
        print(f"  {metric:12s}: {value:.4f}  (threshold: {'<=' if metric == 'ece' else '>='}{threshold})  [{status}]")

    print(f"\n{'=== ALL THRESHOLDS MET ===' if all_pass else '=== VALIDATION FAILED ==='}")
    return 0 if all_pass else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--model-dir",
        default="models/xgboost-v1.0.0",
        help="Path to model artefact directory",
    )
    args = parser.parse_args()
    sys.exit(main(Path(args.model_dir)))
