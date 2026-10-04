"""Model artefact loader — reads metadata.json to determine model type."""
from __future__ import annotations

import json
import logging
from pathlib import Path

from app.exceptions import ModelLoadError
from app.models.prediction_model_port import PredictionModelPort

logger = logging.getLogger(__name__)


def load_model(model_dir: Path) -> PredictionModelPort:
    """
    Load the prediction model from the given directory.
    Reads metadata.json to determine model type.

    Raises:
        ModelLoadError: if the artefact is missing or corrupt.
    """
    metadata_path = model_dir / "metadata.json"
    if not metadata_path.exists():
        raise ModelLoadError(
            f"metadata.json not found in {model_dir}. "
            "Run scripts/train_synthetic_model.py to generate model artefacts."
        )

    with open(metadata_path) as f:
        meta = json.load(f)

    model_type = meta.get("type", "xgboost")
    logger.info("Loading model type=%s from %s", model_type, model_dir)

    if model_type == "xgboost":
        from app.models.xgboost_model import XGBoostPredictionModel
        return XGBoostPredictionModel(model_dir)

    raise ModelLoadError(f"Unknown model type: {model_type}")
