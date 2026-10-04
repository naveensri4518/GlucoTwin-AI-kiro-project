"""
Integration tests for the ML service prediction API.
These run against the FastAPI app directly (no real model required for schema tests).
"""
from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from unittest.mock import MagicMock, patch

import pytest
from fastapi.testclient import TestClient

from app.schemas.prediction import ConfidenceIntervalDTO, ContributingFactorDTO, PredictionResponseDTO
from app.schemas.twin_state import CgmReadingDTO, DynamicLayerDTO, StaticLayerDTO, TwinStateSnapshotDTO


VALID_TOKEN = "dev-internal-token"
INVALID_TOKEN = "bad-token"


def make_full_snapshot() -> dict[str, Any]:
    now = datetime.now(timezone.utc).isoformat()
    # CGM history with timestamps spread over 60 minutes for realistic slope computation
    from datetime import timedelta
    base_dt = datetime.now(timezone.utc) - timedelta(minutes=60)
    cgm_history = [
        {"value": round(7.0 + i * 0.3, 1), "timestamp": (base_dt + timedelta(minutes=i * 10)).isoformat()}
        for i in range(7)
    ]
    return {
        "patient_id": "a1000000-0000-0000-0000-000000000001",
        "twin_version": 5,
        "status": "ACTIVE",
        "static_layer": {"bmi": 28.5, "hba1c": 7.2},
        "dynamic_layer": {
            "glucose_reading": 8.4,
            "heart_rate": 72.0,
            "hrv": 45.0,
            "sleep_duration": 6.5,
            "step_count": 4200,
            "activity_level": "LIGHT",
            "event_timestamp": now,
            "cgm_history": cgm_history,
        },
        "snapshot_taken_at": now,
    }


def make_minimal_snapshot() -> dict[str, Any]:
    now = datetime.now(timezone.utc).isoformat()
    return {
        "patient_id": "a1000000-0000-0000-0000-000000000002",
        "twin_version": 1,
        "status": "ACTIVE",
        "static_layer": {},
        "dynamic_layer": {
            "glucose_reading": 9.0,
            "event_timestamp": now,
        },
        "snapshot_taken_at": now,
    }


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def mock_model() -> MagicMock:
    """A mock PredictionModelPort that returns a valid response."""
    model = MagicMock()
    model.get_model_version.return_value = "xgboost-v1.0.0"
    model.predict.return_value = PredictionResponseDTO(
        spike_probability=0.72,
        confidence_interval=ConfidenceIntervalDTO(low=0.61, high=0.81),
        top_contributing_factors=[
            ContributingFactorDTO(
                factor_name="cgm_current",
                contribution=0.31,
                direction="INCREASES_RISK",
            )
        ],
        model_version="xgboost-v1.0.0",
        imputed_fields=[],
    )
    return model


@pytest.fixture
def client(mock_model: MagicMock) -> TestClient:
    """TestClient with the model injected via app.state."""
    from main import app
    app.state.model = mock_model
    return TestClient(app, raise_server_exceptions=False)


# ---------------------------------------------------------------------------
# Health endpoint (no token required)
# ---------------------------------------------------------------------------

class TestHealthEndpoint:
    def test_health_returns_200_without_token(self, client: TestClient) -> None:
        response = client.get("/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "ok"

    def test_health_includes_model_version(self, client: TestClient) -> None:
        response = client.get("/health")
        data = response.json()
        assert "modelVersion" in data


# ---------------------------------------------------------------------------
# Internal token authentication
# ---------------------------------------------------------------------------

class TestInternalTokenAuth:
    def test_predict_without_token_returns_401(self, client: TestClient) -> None:
        response = client.post("/predict", json=make_full_snapshot())
        assert response.status_code == 401

    def test_predict_with_wrong_token_returns_401(self, client: TestClient) -> None:
        response = client.post(
            "/predict",
            json=make_full_snapshot(),
            headers={"X-Internal-Token": INVALID_TOKEN},
        )
        assert response.status_code == 401

    def test_health_exempt_from_token(self, client: TestClient) -> None:
        response = client.get("/health")
        assert response.status_code == 200


# ---------------------------------------------------------------------------
# Prediction endpoint — happy paths
# ---------------------------------------------------------------------------

class TestPredictEndpoint:
    def test_full_snapshot_returns_200_with_valid_schema(
        self, client: TestClient, mock_model: MagicMock
    ) -> None:
        response = client.post(
            "/predict",
            json=make_full_snapshot(),
            headers={"X-Internal-Token": VALID_TOKEN},
        )
        assert response.status_code == 200
        data = response.json()
        # Schema validation
        assert "spike_probability" in data
        assert "confidence_interval" in data
        assert "top_contributing_factors" in data
        assert "model_version" in data
        assert "imputed_fields" in data

    def test_spike_probability_in_unit_interval(
        self, client: TestClient
    ) -> None:
        response = client.post(
            "/predict",
            json=make_full_snapshot(),
            headers={"X-Internal-Token": VALID_TOKEN},
        )
        prob = response.json()["spike_probability"]
        assert 0.0 <= prob <= 1.0

    def test_confidence_interval_valid(self, client: TestClient) -> None:
        response = client.post(
            "/predict",
            json=make_full_snapshot(),
            headers={"X-Internal-Token": VALID_TOKEN},
        )
        ci = response.json()["confidence_interval"]
        assert ci["low"] <= response.json()["spike_probability"] <= ci["high"]
        assert ci["high"] > ci["low"]

    def test_minimal_snapshot_with_missing_optional_fields(
        self, client: TestClient, mock_model: MagicMock
    ) -> None:
        """Missing optional fields should be imputed, not cause errors."""
        mock_model.predict.return_value = PredictionResponseDTO(
            spike_probability=0.55,
            confidence_interval=ConfidenceIntervalDTO(low=0.44, high=0.66),
            top_contributing_factors=[],
            model_version="xgboost-v1.0.0",
            imputed_fields=["hrv_current", "bmi", "sleep_duration_last"],
        )
        response = client.post(
            "/predict",
            json=make_minimal_snapshot(),
            headers={"X-Internal-Token": VALID_TOKEN},
        )
        assert response.status_code == 200
        data = response.json()
        assert len(data["imputed_fields"]) > 0

    def test_model_version_present_in_response(
        self, client: TestClient
    ) -> None:
        response = client.post(
            "/predict",
            json=make_full_snapshot(),
            headers={"X-Internal-Token": VALID_TOKEN},
        )
        assert response.json()["model_version"] == "xgboost-v1.0.0"


# ---------------------------------------------------------------------------
# Prediction endpoint — error cases
# ---------------------------------------------------------------------------

class TestPredictErrors:
    def test_missing_glucose_returns_422(self, client: TestClient) -> None:
        snapshot = make_full_snapshot()
        snapshot["dynamic_layer"]["glucose_reading"] = None
        response = client.post(
            "/predict",
            json=snapshot,
            headers={"X-Internal-Token": VALID_TOKEN},
        )
        assert response.status_code == 422
        assert "GLUCOSE_READING_REQUIRED" in response.json().get("error", "")

    def test_invalid_snapshot_schema_returns_422(
        self, client: TestClient
    ) -> None:
        response = client.post(
            "/predict",
            json={"bad_field": "not_a_snapshot"},
            headers={"X-Internal-Token": VALID_TOKEN},
        )
        assert response.status_code == 422

    def test_model_unavailable_returns_503(
        self, client: TestClient, mock_model: MagicMock
    ) -> None:
        """When model is None (not loaded), /predict should return 503."""
        client.app.state.model = None
        response = client.post(
            "/predict",
            json=make_full_snapshot(),
            headers={"X-Internal-Token": VALID_TOKEN},
        )
        assert response.status_code == 503
        # Restore
        client.app.state.model = mock_model


# ---------------------------------------------------------------------------
# Safety checks on API responses
# ---------------------------------------------------------------------------

FORBIDDEN_TERMS = [
    "diagnose", "diagnosis", "you have", "confirms", "prescribe",
    "prescription", "guaranteed", "certain",
]


class TestApiSafetyConstraints:
    def test_response_contains_no_diagnostic_language(
        self, client: TestClient
    ) -> None:
        response = client.post(
            "/predict",
            json=make_full_snapshot(),
            headers={"X-Internal-Token": VALID_TOKEN},
        )
        response_text = response.text.lower()
        for term in FORBIDDEN_TERMS:
            assert term not in response_text, \
                f"Forbidden term '{term}' found in API response: {response.text}"

    def test_response_does_not_claim_observed_provenance(
        self, client: TestClient
    ) -> None:
        response = client.post(
            "/predict",
            json=make_full_snapshot(),
            headers={"X-Internal-Token": VALID_TOKEN},
        )
        assert "OBSERVED" not in response.text
        assert "SIMULATED" not in response.text
