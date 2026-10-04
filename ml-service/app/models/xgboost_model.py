"""XGBoost v1 implementation of PredictionModelPort."""
from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import List

import numpy as np
import joblib

from app.exceptions import ModelLoadError
from app.features.feature_engineering import FEATURE_NAMES, FeatureVector
from app.models.prediction_model_port import PredictionModelPort
from app.schemas.prediction import (
    ConfidenceIntervalDTO,
    ContributingFactorDTO,
    PredictionResponseDTO,
)

logger = logging.getLogger(__name__)


class XGBoostPredictionModel(PredictionModelPort):
    """
    XGBoost-based glucose spike predictor with SHAP explanations
    and conformal prediction confidence intervals.
    """

    def __init__(self, model_dir: Path) -> None:
        model_path = model_dir / "model.joblib"
        explainer_path = model_dir / "explainer.joblib"
        calibration_path = model_dir / "calibration_scores.npy"
        metadata_path = model_dir / "metadata.json"

        for p in [model_path, metadata_path]:
            if not p.exists():
                raise ModelLoadError(f"Required model artefact not found: {p}")

        try:
            self._model = joblib.load(model_path)
            self._version = self._read_version(metadata_path)
            logger.info("Loaded XGBoost model version %s", self._version)
        except Exception as e:
            raise ModelLoadError(f"Failed to load model artefact: {e}") from e

        # SHAP explainer (optional — skip if not present)
        self._explainer = None
        if explainer_path.exists():
            try:
                self._explainer = joblib.load(explainer_path)
                logger.info("Loaded SHAP TreeExplainer")
            except Exception as e:
                logger.warning("Failed to load SHAP explainer: %s — explanations will be unavailable", e)

        # Calibration scores for conformal CI (optional)
        self._calibration_scores: np.ndarray | None = None
        if calibration_path.exists():
            try:
                self._calibration_scores = np.load(calibration_path)
                logger.info("Loaded %d calibration scores", len(self._calibration_scores))
            except Exception as e:
                logger.warning("Failed to load calibration scores: %s — CI will use fallback", e)

    def predict(self, features: FeatureVector) -> PredictionResponseDTO:
        X = features.to_numpy_array()

        # Spike probability
        raw_prob = float(self._model.predict_proba(X)[0, 1])
        prob = max(0.0, min(1.0, raw_prob))

        if raw_prob != prob:
            logger.warning("Model probability clamped from %.6f to %.6f", raw_prob, prob)

        # Confidence interval
        ci = self._compute_confidence_interval(prob)

        # SHAP contributing factors
        top_factors = self._compute_shap_factors(X, features)

        return PredictionResponseDTO(
            spike_probability=prob,
            confidence_interval=ci,
            top_contributing_factors=top_factors,
            model_version=self._version,
            imputed_fields=list(features.imputed_fields),
        )

    def get_model_version(self) -> str:
        return self._version

    # -------------------------------------------------------------------------
    # Private helpers
    # -------------------------------------------------------------------------

    def _compute_confidence_interval(self, prob: float) -> ConfidenceIntervalDTO:
        """
        Conformal prediction CI.
        Uses calibration scores if available; falls back to ±0.15 heuristic.
        """
        if self._calibration_scores is not None and len(self._calibration_scores) > 0:
            alpha = 0.05  # 95% CI
            q_hat = float(np.quantile(self._calibration_scores, 1 - alpha))
            low = max(0.0, prob - q_hat)
            high = min(1.0, prob + q_hat)
        else:
            margin = 0.15
            low = max(0.0, prob - margin)
            high = min(1.0, prob + margin)

        # Ensure non-degenerate interval
        if low >= high:
            low = max(0.0, prob - 0.01)
            high = min(1.0, prob + 0.01)
        if low >= high:
            low = 0.0
            high = 1.0

        return ConfidenceIntervalDTO(low=low, high=high)

    def _compute_shap_factors(
        self, X: np.ndarray, features: FeatureVector
    ) -> List[ContributingFactorDTO]:
        """Compute top-5 SHAP contributing factors."""
        if self._explainer is None:
            return self._fallback_factors(features)

        try:
            shap_values = self._explainer.shap_values(X)
            # For binary classification, shap_values may be a list [neg_class, pos_class]
            if isinstance(shap_values, list):
                sv = shap_values[1][0] if len(shap_values) > 1 else shap_values[0][0]
            else:
                sv = shap_values[0]

            # Get top 5 by absolute value
            abs_values = np.abs(sv)
            top_indices = np.argsort(abs_values)[::-1][:5]

            factors = []
            for idx in top_indices:
                contribution = float(sv[idx])
                direction = "INCREASES_RISK" if contribution > 0 else "DECREASES_RISK"
                factors.append(ContributingFactorDTO(
                    factor_name=FEATURE_NAMES[idx],
                    contribution=abs(contribution),
                    direction=direction,
                ))
            return factors
        except Exception as e:
            logger.warning("SHAP computation failed: %s — using fallback factors", e)
            return self._fallback_factors(features)

    def _fallback_factors(self, features: FeatureVector) -> List[ContributingFactorDTO]:
        """Deterministic fallback when SHAP is unavailable."""
        return [
            ContributingFactorDTO(
                factor_name="cgm_current",
                contribution=features.features.get("cgm_current", 0.0) / 35.0,
                direction="INCREASES_RISK",
            )
        ]

    @staticmethod
    def _read_version(metadata_path: Path) -> str:
        with open(metadata_path) as f:
            meta = json.load(f)
        return str(meta.get("version", "xgboost-v1.0.0"))
