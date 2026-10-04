"""FastAPI prediction router."""
from __future__ import annotations

import logging

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse

from app.exceptions import GlucoseReadingRequiredError, ModelLoadError
from app.features.feature_engineering import FeatureEngineer
from app.schemas.prediction import PredictionResponseDTO
from app.schemas.twin_state import TwinStateSnapshotDTO

router = APIRouter()
logger = logging.getLogger(__name__)

# Singleton feature engineer (initialised once on first request)
_feature_engineer: FeatureEngineer | None = None


def _get_feature_engineer(request: Request) -> FeatureEngineer:
    global _feature_engineer
    if _feature_engineer is None:
        from pathlib import Path
        from app.config import settings
        imp_path = Path(settings.model_dir) / "imputation_config.json"
        _feature_engineer = FeatureEngineer(imputation_config_path=imp_path)
    return _feature_engineer


@router.post("/predict", response_model=PredictionResponseDTO, tags=["Prediction"])
async def predict_spike(
    snapshot: TwinStateSnapshotDTO,
    request: Request,
) -> PredictionResponseDTO:
    """
    Predict glucose spike probability for the given twin state snapshot.

    This endpoint is for research and decision support only.
    It does not diagnose, prescribe, or guarantee medical outcomes.
    spikeProbability is a probabilistic estimate, not a certainty.
    """
    model = getattr(request.app.state, "model", None)
    if model is None:
        raise ModelLoadError("Model not loaded")

    feature_engineer = _get_feature_engineer(request)

    # Extract features (raises GlucoseReadingRequiredError if glucose absent)
    features = feature_engineer.extract(snapshot)

    logger.debug(
        "FEATURE_VECTOR_COMPUTED patient_id=%s features=%s imputed=%s",
        snapshot.patient_id,
        list(features.features.keys()),
        features.imputed_fields,
    )

    # Run prediction
    result = model.predict(features)

    logger.info(
        "PREDICTION_COMPLETED patient_id=%s spike_probability=%.4f risk_level=%s model_version=%s",
        snapshot.patient_id,
        result.spike_probability,
        _risk_level(result.spike_probability),
        result.model_version,
    )

    return result


def _risk_level(prob: float) -> str:
    if prob >= 0.85:
        return "CRITICAL"
    if prob >= 0.60:
        return "HIGH"
    if prob >= 0.30:
        return "MODERATE"
    return "LOW"
