"""Unit tests for FeatureEngineer."""
import math
import pytest
from datetime import datetime, timezone, date

from app.exceptions import GlucoseReadingRequiredError
from app.features.feature_engineering import FEATURE_NAMES, FeatureEngineer
from app.schemas.twin_state import (
    CgmReadingDTO, DynamicLayerDTO, StaticLayerDTO, TwinStateSnapshotDTO,
)


@pytest.fixture
def engineer() -> FeatureEngineer:
    return FeatureEngineer()


def make_snapshot(
    glucose: float | None = 8.4,
    heart_rate: float | None = 72.0,
    hrv: float | None = 45.0,
    sleep_duration: float | None = 6.5,
    step_count: int | None = 4200,
    activity_level: str | None = "LIGHT",
    bmi: float | None = 28.5,
    hba1c: float | None = 7.2,
    onset_date: date | None = date(2015, 3, 1),
    cgm_history: list[CgmReadingDTO] | None = None,
) -> TwinStateSnapshotDTO:
    now = datetime.now(timezone.utc)
    if cgm_history is None:
        cgm_history = [
            CgmReadingDTO(value=7.0 + i * 0.3, timestamp=datetime(2026, 10, 4, 10, i * 5, 0, tzinfo=timezone.utc))
            for i in range(6)
        ]
    return TwinStateSnapshotDTO(
        patient_id="test-patient",
        twin_version=1,
        status="ACTIVE",
        static_layer=StaticLayerDTO(bmi=bmi, hba1c=hba1c, diabetes_onset_date=onset_date),
        dynamic_layer=DynamicLayerDTO(
            glucose_reading=glucose,
            heart_rate=heart_rate,
            hrv=hrv,
            sleep_duration=sleep_duration,
            step_count=step_count,
            activity_level=activity_level,
            event_timestamp=now,
            cgm_history=cgm_history,
        ),
        snapshot_taken_at=now,
    )


class TestFeatureEngineerAllFields:
    def test_all_14_features_present(self, engineer: FeatureEngineer) -> None:
        fv = engineer.extract(make_snapshot())
        for name in FEATURE_NAMES:
            assert name in fv.features, f"Missing feature: {name}"

    def test_cgm_current_equals_glucose_reading(self, engineer: FeatureEngineer) -> None:
        fv = engineer.extract(make_snapshot(glucose=9.5))
        assert fv.features["cgm_current"] == pytest.approx(9.5)

    def test_time_encoding_hour_6(self, engineer: FeatureEngineer) -> None:
        snap = make_snapshot()
        snap.dynamic_layer.event_timestamp = datetime(2026, 10, 4, 6, 0, 0, tzinfo=timezone.utc)
        fv = engineer.extract(snap)
        expected_sin = math.sin(2 * math.pi * 6 / 24)
        expected_cos = math.cos(2 * math.pi * 6 / 24)
        assert fv.features["time_of_day_sin"] == pytest.approx(expected_sin, abs=1e-6)
        assert fv.features["time_of_day_cos"] == pytest.approx(expected_cos, abs=1e-6)

    def test_activity_level_encoding(self, engineer: FeatureEngineer) -> None:
        fv = engineer.extract(make_snapshot(activity_level="SEDENTARY"))
        assert fv.features["activity_level_enc"] == 0.0
        fv2 = engineer.extract(make_snapshot(activity_level="VIGOROUS"))
        assert fv2.features["activity_level_enc"] == 3.0


class TestImputation:
    def test_missing_hrv_is_imputed(self, engineer: FeatureEngineer) -> None:
        fv = engineer.extract(make_snapshot(hrv=None))
        assert "hrv_current" in fv.imputed_fields

    def test_missing_activity_level_is_imputed(self, engineer: FeatureEngineer) -> None:
        fv = engineer.extract(make_snapshot(activity_level=None))
        assert "activity_level_enc" in fv.imputed_fields

    def test_missing_bmi_is_imputed(self, engineer: FeatureEngineer) -> None:
        fv = engineer.extract(make_snapshot(bmi=None))
        assert "bmi" in fv.imputed_fields

    def test_missing_onset_date_imputes_days_since_diagnosis(self, engineer: FeatureEngineer) -> None:
        fv = engineer.extract(make_snapshot(onset_date=None))
        assert "days_since_diagnosis" in fv.imputed_fields


class TestGlucoseRequired:
    def test_missing_glucose_raises_error(self, engineer: FeatureEngineer) -> None:
        with pytest.raises(GlucoseReadingRequiredError):
            engineer.extract(make_snapshot(glucose=None))

    def test_none_dynamic_layer_raises_error(self, engineer: FeatureEngineer) -> None:
        snap = make_snapshot()
        snap.dynamic_layer = None
        with pytest.raises(GlucoseReadingRequiredError):
            engineer.extract(snap)


class TestCgmHistory:
    def test_empty_history_imputes_delta_and_slope(self, engineer: FeatureEngineer) -> None:
        fv = engineer.extract(make_snapshot(cgm_history=[]))
        assert "cgm_delta_30m" in fv.imputed_fields
        assert "cgm_slope_60m" in fv.imputed_fields

    def test_two_readings_computes_delta_imputes_slope(self, engineer: FeatureEngineer) -> None:
        history = [
            CgmReadingDTO(value=7.0, timestamp=datetime(2026, 10, 4, 10, 0, 0, tzinfo=timezone.utc)),
            CgmReadingDTO(value=8.0, timestamp=datetime(2026, 10, 4, 10, 25, 0, tzinfo=timezone.utc)),
        ]
        fv = engineer.extract(make_snapshot(cgm_history=history))
        assert "cgm_delta_30m" not in fv.imputed_fields
        assert fv.features["cgm_delta_30m"] == pytest.approx(1.0, abs=0.01)
        assert "cgm_slope_60m" in fv.imputed_fields

    def test_linear_slope_correct_for_known_values(self, engineer: FeatureEngineer) -> None:
        history = [
            CgmReadingDTO(value=6.0, timestamp=datetime(2026, 10, 4, 10, 0, 0, tzinfo=timezone.utc)),
            CgmReadingDTO(value=7.0, timestamp=datetime(2026, 10, 4, 10, 10, 0, tzinfo=timezone.utc)),
            CgmReadingDTO(value=8.0, timestamp=datetime(2026, 10, 4, 10, 20, 0, tzinfo=timezone.utc)),
        ]
        fv = engineer.extract(make_snapshot(cgm_history=history))
        # Slope should be ~0.1 mmol/L per minute
        assert fv.features["cgm_slope_60m"] == pytest.approx(0.1, abs=0.01)


class TestNoNanOrInfinity:
    def test_all_features_finite_for_full_snapshot(self, engineer: FeatureEngineer) -> None:
        fv = engineer.extract(make_snapshot())
        for name, value in fv.features.items():
            assert math.isfinite(value), f"Feature {name} is not finite: {value}"

    def test_all_features_finite_for_minimal_snapshot(self, engineer: FeatureEngineer) -> None:
        minimal = make_snapshot(
            hrv=None, sleep_duration=None, step_count=None, activity_level=None,
            bmi=None, hba1c=None, onset_date=None, cgm_history=[]
        )
        fv = engineer.extract(minimal)
        for name, value in fv.features.items():
            assert math.isfinite(value), f"Feature {name} is not finite: {value}"
