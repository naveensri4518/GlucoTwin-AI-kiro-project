"""Feature engineering pipeline for glucose spike prediction."""
from __future__ import annotations

import json
import logging
import math
from datetime import date, datetime, timezone
from pathlib import Path
from typing import List, Optional

import numpy as np

from app.exceptions import FeatureExtractionError, GlucoseReadingRequiredError
from app.schemas.twin_state import CgmReadingDTO, TwinStateSnapshotDTO

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Feature names (canonical order — must match model training)
# ---------------------------------------------------------------------------
FEATURE_NAMES: List[str] = [
    "cgm_current",
    "cgm_delta_30m",
    "cgm_mean_60m",
    "cgm_slope_60m",
    "heart_rate_current",
    "hrv_current",
    "sleep_duration_last",
    "step_count_today",
    "activity_level_enc",
    "hba1c_latest",
    "bmi",
    "time_of_day_sin",
    "time_of_day_cos",
    "days_since_diagnosis",
]

_ACTIVITY_ENCODING = {
    "SEDENTARY": 0,
    "LIGHT": 1,
    "MODERATE": 2,
    "VIGOROUS": 3,
}

# Default imputation medians (overridden by imputation_config.json at startup)
_DEFAULT_MEDIANS = {
    "heart_rate_median": 72.0,
    "hrv_median": 45.0,
    "sleep_median": 7.0,
    "step_median": 5000,
    "hba1c_median": 7.0,
    "bmi_median": 27.5,
    "cgm_delta_30m_median": 0.0,
    "cgm_mean_60m_median": 7.5,
    "cgm_slope_60m_median": 0.0,
    "activity_enc_median": 1,
    "days_since_diagnosis_median": 1825,  # ~5 years
}


class FeatureVector:
    """Holds the extracted features and metadata about imputed fields."""

    def __init__(self, features: dict[str, float], imputed_fields: list[str]) -> None:
        self.features = features
        self.imputed_fields = imputed_fields

    def to_numpy_array(self) -> np.ndarray:
        """Returns features as a 1×N numpy array in canonical FEATURE_NAMES order."""
        return np.array([[self.features[name] for name in FEATURE_NAMES]], dtype=np.float64)


class FeatureEngineer:
    """
    Pure function feature extractor.
    Same input always produces the same output.
    """

    def __init__(self, imputation_config_path: Optional[Path] = None) -> None:
        self._medians = dict(_DEFAULT_MEDIANS)
        if imputation_config_path and imputation_config_path.exists():
            try:
                with open(imputation_config_path) as f:
                    loaded = json.load(f)
                self._medians.update(loaded)
                logger.info("Loaded imputation config from %s", imputation_config_path)
            except Exception as e:
                logger.warning("Failed to load imputation config: %s — using defaults", e)

    # -------------------------------------------------------------------------
    # Public API
    # -------------------------------------------------------------------------

    def extract(self, snapshot: TwinStateSnapshotDTO) -> FeatureVector:
        """
        Extract the 14 features from a TwinStateSnapshotDTO.

        Raises:
            GlucoseReadingRequiredError: if glucose reading is absent.
        """
        imputed: list[str] = []
        features: dict[str, float] = {}

        dyn = snapshot.dynamic_layer
        static = snapshot.static_layer

        # ----- Primary glucose signal (required) -----
        if dyn is None or dyn.glucose_reading is None:
            raise GlucoseReadingRequiredError("Glucose reading is required for prediction")
        features["cgm_current"] = float(dyn.glucose_reading)

        # ----- Temporal glucose features (from CGM history) -----
        history = (dyn.cgm_history or []) if dyn else []
        features["cgm_delta_30m"] = self._cgm_delta(history, minutes=30, imputed=imputed)
        features["cgm_mean_60m"] = self._cgm_rolling_mean(history, minutes=60, imputed=imputed)
        features["cgm_slope_60m"] = self._cgm_linear_slope(history, minutes=60, imputed=imputed)

        # ----- Wearable signals (imputed if missing) -----
        features["heart_rate_current"] = self._impute(
            dyn.heart_rate if dyn else None, "heart_rate_median", imputed, "heart_rate_current"
        )
        features["hrv_current"] = self._impute(
            dyn.hrv if dyn else None, "hrv_median", imputed, "hrv_current"
        )
        features["sleep_duration_last"] = self._impute(
            dyn.sleep_duration if dyn else None, "sleep_median", imputed, "sleep_duration_last"
        )
        features["step_count_today"] = float(self._impute(
            float(dyn.step_count) if (dyn and dyn.step_count is not None) else None,
            "step_median", imputed, "step_count_today"
        ))
        features["activity_level_enc"] = self._encode_activity(
            dyn.activity_level if dyn else None, imputed
        )

        # ----- Static EHR features -----
        hba1c = static.hba1c if static else None
        features["hba1c_latest"] = self._impute(hba1c, "hba1c_median", imputed, "hba1c_latest")

        bmi = static.bmi if static else None
        features["bmi"] = self._impute(bmi, "bmi_median", imputed, "bmi")

        features["days_since_diagnosis"] = self._days_since(
            static.diabetes_onset_date if static else None, imputed
        )

        # ----- Cyclical time encoding -----
        ts = (dyn.event_timestamp if dyn else None) or snapshot.snapshot_taken_at
        hour = ts.hour if ts else 12
        features["time_of_day_sin"] = math.sin(2 * math.pi * hour / 24)
        features["time_of_day_cos"] = math.cos(2 * math.pi * hour / 24)

        # Validate no NaN / Infinity
        for name, value in features.items():
            if not math.isfinite(value):
                raise FeatureExtractionError(f"Feature {name} is not finite: {value}")

        logger.debug("Feature vector extracted: features=%s imputed=%s", list(features.keys()), imputed)
        return FeatureVector(features, imputed)

    # -------------------------------------------------------------------------
    # Private helpers
    # -------------------------------------------------------------------------

    def _impute(
        self,
        value: Optional[float],
        config_key: str,
        imputed: list[str],
        field_name: str,
    ) -> float:
        if value is not None:
            return float(value)
        imputed.append(field_name)
        return float(self._medians.get(config_key, 0.0))

    def _cgm_delta(
        self, history: list[CgmReadingDTO], minutes: int, imputed: list[str]
    ) -> float:
        """Change in glucose over the last `minutes` minutes."""
        if len(history) < 2:
            imputed.append("cgm_delta_30m")
            return float(self._medians.get("cgm_delta_30m_median", 0.0))

        sorted_h = sorted(history, key=lambda r: r.timestamp)
        latest = sorted_h[-1]
        cutoff = latest.timestamp.replace(tzinfo=timezone.utc) - _timedelta_minutes(minutes)

        past_readings = [
            r for r in sorted_h[:-1]
            if r.timestamp.replace(tzinfo=timezone.utc) >= cutoff
        ]
        if not past_readings:
            imputed.append("cgm_delta_30m")
            return float(self._medians.get("cgm_delta_30m_median", 0.0))

        oldest_in_window = past_readings[0]
        return float(latest.value - oldest_in_window.value)

    def _cgm_rolling_mean(
        self, history: list[CgmReadingDTO], minutes: int, imputed: list[str]
    ) -> float:
        readings_in_window = self._readings_in_window(history, minutes)
        if not readings_in_window:
            imputed.append("cgm_mean_60m")
            return float(self._medians.get("cgm_mean_60m_median", 7.5))
        return float(np.mean([r.value for r in readings_in_window]))

    def _cgm_linear_slope(
        self, history: list[CgmReadingDTO], minutes: int, imputed: list[str]
    ) -> float:
        """Linear regression slope of CGM over last `minutes` minutes (mmol/L per minute)."""
        readings = self._readings_in_window(history, minutes)
        if len(readings) < 3:
            imputed.append("cgm_slope_60m")
            return float(self._medians.get("cgm_slope_60m_median", 0.0))

        sorted_r = sorted(readings, key=lambda r: r.timestamp)
        base_ts = sorted_r[0].timestamp
        times_min = [
            (r.timestamp.replace(tzinfo=timezone.utc) - base_ts.replace(tzinfo=timezone.utc)).total_seconds() / 60
            for r in sorted_r
        ]
        values = [r.value for r in sorted_r]

        # Guard: if all time points are identical, slope is 0 (no time has passed)
        if max(times_min) - min(times_min) < 1e-6:
            imputed.append("cgm_slope_60m")
            return float(self._medians.get("cgm_slope_60m_median", 0.0))

        try:
            coeffs = np.polyfit(times_min, values, 1)
            return float(coeffs[0])
        except (np.linalg.LinAlgError, Exception):
            imputed.append("cgm_slope_60m")
            return float(self._medians.get("cgm_slope_60m_median", 0.0))

    def _readings_in_window(
        self, history: list[CgmReadingDTO], minutes: int
    ) -> list[CgmReadingDTO]:
        if not history:
            return []
        sorted_h = sorted(history, key=lambda r: r.timestamp)
        latest_ts = sorted_h[-1].timestamp.replace(tzinfo=timezone.utc)
        cutoff = latest_ts - _timedelta_minutes(minutes)
        return [r for r in sorted_h if r.timestamp.replace(tzinfo=timezone.utc) >= cutoff]

    def _encode_activity(self, level: Optional[str], imputed: list[str]) -> float:
        if level is None:
            imputed.append("activity_level_enc")
            return float(self._medians.get("activity_enc_median", 1))
        return float(_ACTIVITY_ENCODING.get(level.upper(), 1))

    def _days_since(self, onset_date: Optional[date], imputed: list[str]) -> float:
        if onset_date is None:
            imputed.append("days_since_diagnosis")
            return float(self._medians.get("days_since_diagnosis_median", 1825))
        today = date.today()
        delta = (today - onset_date).days
        return max(0.0, float(delta))


def _timedelta_minutes(minutes: int):  # type: ignore[return]
    from datetime import timedelta
    return timedelta(minutes=minutes)
