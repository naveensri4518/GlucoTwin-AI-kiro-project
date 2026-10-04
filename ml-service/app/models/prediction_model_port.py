"""Abstract base class for prediction models."""
from __future__ import annotations

from abc import ABC, abstractmethod

from app.features.feature_engineering import FeatureVector
from app.schemas.prediction import ConfidenceIntervalDTO, ContributingFactorDTO, PredictionResponseDTO


class PredictionModelPort(ABC):
    """
    Replaceable ML model abstraction.
    Implementations: XGBoostPredictionModel (production), MockPredictionModel (tests).
    """

    @abstractmethod
    def predict(self, features: FeatureVector) -> PredictionResponseDTO:
        """
        Predict glucose spike probability.

        Returns a PredictionResponseDTO with:
        - spike_probability in [0.0, 1.0]
        - confidence_interval (95% CI)
        - top_contributing_factors (≤5 SHAP-derived factors)
        - model_version
        - imputed_fields
        """

    @abstractmethod
    def get_model_version(self) -> str:
        """Returns the model artefact version string."""
