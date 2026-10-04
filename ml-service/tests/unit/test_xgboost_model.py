"""Unit tests for XGBoostPredictionModel."""
from __future__ import annotations

import math
from datetime import datetime, timezone
from pathlib import Path

import pytest

from app.features.feature_engineering import FeatureEngineer, FeatureVector
from app.schemas.twin_state import CgmReadingDTO, DynamicLayerDTO, StaticLayerDTO, TwinStateSnapshotDTO

MODEL_DIR = Path(__file__).parent.parent.parent / "models" / "xgboost-v1.0.0"


@pytest.fixture(scope="module")
def model():
    """Load the XGBoost model once for all tests in this module."""
    if not MODEL_DIR.exists():
        pytest.skip("Model artefacts not found — run scripts/train_synthetic_model.py first")
    from app.models.xgboost_model import XGBoostPredictionModel
    return XGBoostPredictionModel(MODEL_DIR)


@pytest.fixture(scope="module")
def engineer():
    imp_path = MODEL_DIR / "imputation_config.json"
    return FeatureEngineer(imputation_config_path=imp_path)


def make_feature_vector(glucose: float = 8.4) -> FeatureVector:
    """Create a FeatureVector directly for testing the model in isolation."""
    import numpy as np
    import math

    hour = 10
    return FeatureVector(
        features={
            "cgm_current": glucose,
            "cgm_delta_30m": 0.5,
            "cgm_mean_60m": 8.0,
            "cgm_slope_60m": 0.01,
            "heart_rate_current": 72.0,
            "hrv_current": 45.0,
            "sleep_duration_last": 6.5,
            "step_count_today": 4200.0,
            "activity_level_enc": 1.0,
            "hba1c_latest": 7.2,
            "bmi": 28.5,
            "time_of_day_sin": math.sin(2 * math.pi * hour / 24),
            "time_of_day_cos": math.cos(2 * math.pi * hour / 24),
            "days_since_diagnosis": 3650.0,
        },
        imputed_fields=[],
    )


def make_high_risk_feature_vector() -> FeatureVector:
    """Feature vector designed to produce high spike probability."""
    import math
    hour = 2  # nocturnal
    return FeatureVector(
        features={
            "cgm_current": 18.0,       # very high glucose
            "cgm_delta_30m": 2.5,      # rapidly rising
            "cgm_mean_60m": 15.0,      # high rolling mean
            "cgm_slope_60m": 0.08,     # steep upward slope
            "heart_rate_current": 95.0,
            "hrv_current": 20.0,       # low HRV
            "sleep_duration_last": 4.0,
            "step_count_today": 500.0, # sedentary
            "activity_level_enc": 0.0, # SEDENTARY
            "hba1c_latest": 10.5,      # poor control
            "bmi": 38.0,               # obese
            "time_of_day_sin": math.sin(2 * math.pi * hour / 24),
            "time_of_day_cos": math.cos(2 * math.pi * hour / 24),
            "days_since_diagnosis": 3650.0,
        },
        imputed_fields=[],
    )


class TestXGBoostModelOutputConstraints:
    """REQ-005, REQ-018: Model output must always be valid."""

    def test_spike_probability_always_in_unit_interval(self, model) -> None:
        fv = make_feature_vector(glucose=8.4)
        result = model.predict(fv)
        assert 0.0 <= result.spike_probability <= 1.0

    def test_confidence_interval_low_lte_probability(self, model) -> None:
        fv = make_feature_vector()
        result = model.predict(fv)
        assert result.confidence_interval.low <= result.spike_probability

    def test_confidence_interval_high_gte_probability(self, model) -> None:
        fv = make_feature_vector()
        result = model.predict(fv)
        assert result.confidence_interval.high >= result.spike_probability

    def test_confidence_interval_width_greater_than_zero(self, model) -> None:
        fv = make_feature_vector()
        result = model.predict(fv)
        assert result.confidence_interval.high > result.confidence_interval.low

    def test_both_ci_bounds_in_unit_interval(self, model) -> None:
        fv = make_feature_vector()
        result = model.predict(fv)
        assert 0.0 <= result.confidence_interval.low <= 1.0
        assert 0.0 <= result.confidence_interval.high <= 1.0

    def test_top_contributing_factors_max_five(self, model) -> None:
        fv = make_feature_vector()
        result = model.predict(fv)
        assert len(result.top_contributing_factors) <= 5

    def test_contributing_factors_have_float_contributions(self, model) -> None:
        fv = make_feature_vector()
        result = model.predict(fv)
        for factor in result.top_contributing_factors:
            assert isinstance(factor.contribution, float)
            assert math.isfinite(factor.contribution)

    def test_model_version_matches_metadata(self, model) -> None:
        import json
        meta_path = MODEL_DIR / "metadata.json"
        with open(meta_path) as f:
            meta = json.load(f)
        assert model.get_model_version() == meta["version"]

    def test_high_risk_snapshot_returns_probability_above_0_5(self, model) -> None:
        """Smoke test: high-risk features should yield probability > 0.5."""
        fv = make_high_risk_feature_vector()
        result = model.predict(fv)
        assert result.spike_probability > 0.5, (
            f"Expected high-risk input to yield probability > 0.5, got {result.spike_probability}"
        )

    def test_all_outputs_are_finite(self, model) -> None:
        fv = make_feature_vector()
        result = model.predict(fv)
        assert math.isfinite(result.spike_probability)
        assert math.isfinite(result.confidence_interval.low)
        assert math.isfinite(result.confidence_interval.high)


class TestXGBoostModelVersion:
    def test_get_model_version_returns_nonempty_string(self, model) -> None:
        version = model.get_model_version()
        assert isinstance(version, str)
        assert len(version) > 0

    def test_model_version_starts_with_xgboost(self, model) -> None:
        assert model.get_model_version().startswith("xgboost")


class TestModelWithFeatureEngineer:
    """End-to-end: FeatureEngineer -> XGBoostModel pipeline."""

    def test_full_pipeline_snapshot_to_prediction(
        self, model, engineer, sample_snapshot
    ) -> None:
        fv = engineer.extract(sample_snapshot)
        result = model.predict(fv)
        assert 0.0 <= result.spike_probability <= 1.0
        assert result.confidence_interval.low <= result.spike_probability <= result.confidence_interval.high

    def test_pipeline_with_many_missing_fields(self, model, engineer) -> None:
        """Prediction should work even with maximum imputation."""
        now = datetime.now(timezone.utc)
        minimal = TwinStateSnapshotDTO(
            patient_id="test-minimal",
            twin_version=1,
            status="ACTIVE",
            static_layer=StaticLayerDTO(),  # all None
            dynamic_layer=DynamicLayerDTO(
                glucose_reading=9.0,
                event_timestamp=now,
            ),
            snapshot_taken_at=now,
        )
        fv = engineer.extract(minimal)
        result = model.predict(fv)
        assert 0.0 <= result.spike_probability <= 1.0
        # All missing fields should be listed as imputed
        assert len(fv.imputed_fields) > 0
