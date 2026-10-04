"""Shared pytest fixtures."""
import pytest
from datetime import datetime, timezone
from app.schemas.twin_state import (
    TwinStateSnapshotDTO, StaticLayerDTO, DynamicLayerDTO, CgmReadingDTO
)


@pytest.fixture
def sample_snapshot() -> TwinStateSnapshotDTO:
    """Returns a fully-populated synthetic TwinStateSnapshotDTO for testing."""
    now = datetime.now(timezone.utc)
    cgm_history = [
        CgmReadingDTO(value=7.0 + i * 0.3, timestamp=datetime(2026, 10, 4, 10, i * 5, 0, tzinfo=timezone.utc))
        for i in range(12)
    ]
    return TwinStateSnapshotDTO(
        patient_id="00000000-0000-0000-0000-000000000001",
        twin_version=5,
        status="ACTIVE",
        static_layer=StaticLayerDTO(
            bmi=28.5,
            hba1c=7.2,
            fasting_glucose=6.1,
            diabetes_onset_date=None,
            sex="MALE",
        ),
        dynamic_layer=DynamicLayerDTO(
            glucose_reading=8.4,
            heart_rate=72.0,
            hrv=45.0,
            sleep_duration=6.5,
            sleep_stage="LIGHT",
            step_count=4200,
            activity_level="LIGHT",
            event_timestamp=now,
            cgm_history=cgm_history,
        ),
        snapshot_taken_at=now,
    )


@pytest.fixture
def minimal_snapshot() -> TwinStateSnapshotDTO:
    """Returns a minimal snapshot with only glucose reading present."""
    now = datetime.now(timezone.utc)
    return TwinStateSnapshotDTO(
        patient_id="00000000-0000-0000-0000-000000000002",
        twin_version=1,
        status="ACTIVE",
        static_layer=StaticLayerDTO(),
        dynamic_layer=DynamicLayerDTO(
            glucose_reading=9.0,
            event_timestamp=now,
        ),
        snapshot_taken_at=now,
    )
