"""Property-based tests for FeatureEngineer."""
import math
from datetime import datetime, timezone

import pytest
from hypothesis import given, settings as h_settings
from hypothesis import strategies as st

from app.features.feature_engineering import FEATURE_NAMES, FeatureEngineer
from app.schemas.twin_state import CgmReadingDTO, DynamicLayerDTO, StaticLayerDTO, TwinStateSnapshotDTO


def make_snapshot_from_strategy(
    glucose: float,
    heart_rate: float | None,
    step_count: int | None,
    activity_level: str | None,
) -> TwinStateSnapshotDTO:
    now = datetime.now(timezone.utc)
    return TwinStateSnapshotDTO(
        patient_id="prop-test-patient",
        twin_version=1,
        status="ACTIVE",
        static_layer=StaticLayerDTO(),
        dynamic_layer=DynamicLayerDTO(
            glucose_reading=glucose,
            heart_rate=heart_rate,
            step_count=step_count,
            activity_level=activity_level,
            event_timestamp=now,
        ),
        snapshot_taken_at=now,
    )


_engineer = FeatureEngineer()

_activity_levels = st.one_of(
    st.none(),
    st.sampled_from(["SEDENTARY", "LIGHT", "MODERATE", "VIGOROUS"]),
)


@given(
    glucose=st.floats(min_value=1.0, max_value=35.0),
    heart_rate=st.one_of(st.none(), st.floats(min_value=20.0, max_value=300.0)),
    step_count=st.one_of(st.none(), st.integers(min_value=0, max_value=100000)),
    activity_level=_activity_levels,
)
@h_settings(max_examples=200)
def test_all_features_finite_for_any_valid_input(
    glucose: float,
    heart_rate: float | None,
    step_count: int | None,
    activity_level: str | None,
) -> None:
    snap = make_snapshot_from_strategy(glucose, heart_rate, step_count, activity_level)
    fv = _engineer.extract(snap)
    for name, value in fv.features.items():
        assert math.isfinite(value), f"Feature {name} is not finite: {value} (input: glucose={glucose})"


@given(
    glucose=st.floats(min_value=1.0, max_value=35.0),
)
@h_settings(max_examples=100)
def test_extract_is_pure_function(glucose: float) -> None:
    """Same input always produces same output."""
    snap1 = make_snapshot_from_strategy(glucose, 72.0, 5000, "LIGHT")
    snap2 = make_snapshot_from_strategy(glucose, 72.0, 5000, "LIGHT")
    fv1 = _engineer.extract(snap1)
    fv2 = _engineer.extract(snap2)
    for name in FEATURE_NAMES:
        assert fv1.features[name] == pytest.approx(fv2.features[name], abs=1e-9), \
            f"Non-deterministic feature: {name}"


@given(
    glucose=st.floats(min_value=1.0, max_value=35.0),
    heart_rate=st.one_of(st.none(), st.floats(min_value=20.0, max_value=300.0)),
)
@h_settings(max_examples=100)
def test_imputed_fields_are_known_feature_names(
    glucose: float, heart_rate: float | None
) -> None:
    snap = make_snapshot_from_strategy(glucose, heart_rate, None, None)
    fv = _engineer.extract(snap)
    for field in fv.imputed_fields:
        assert field in FEATURE_NAMES, f"Imputed field '{field}' is not a known feature name"
