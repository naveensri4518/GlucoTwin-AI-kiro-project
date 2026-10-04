"""
GlucoTwin AI Load Test — Wearable Event Stream
Tests REQ-029 (end-to-end p95 <= 3s) and REQ-031 (100 patients @ 1 event/min).

Usage:
    pip install locust
    locust -f locustfile.py --host http://localhost:8080 --users 100 --spawn-rate 10 --run-time 5m

Results summary is written to load_test_results.json.
"""
from __future__ import annotations

import json
import time
import uuid
from datetime import datetime, timezone
from typing import Any

from locust import HttpUser, between, events, task


# Synthetic patients — loaded once at startup
PATIENT_IDS = [str(uuid.uuid4()) for _ in range(100)]

# Dev auth token (no real JWT in prototype)
DEV_AUTH_HEADER = {"X-Dev-Auth": "ROLE_CLINICIAN"}


def make_wearable_payload() -> dict[str, Any]:
    """Generate a synthetic wearable event payload."""
    return {
        "glucoseReading": round(6.5 + (time.time() % 5), 1),  # 6.5 – 11.5 mmol/L cycling
        "heartRate": 72.0,
        "hrv": 45.0,
        "sleepDuration": 6.5,
        "sleepStage": "LIGHT",
        "stepCount": 4200,
        "activityLevel": "LIGHT",
        "eventTimestamp": datetime.now(timezone.utc).isoformat(),
    }


def make_ehr_payload() -> dict[str, Any]:
    return {
        "dateOfBirth": "1975-06-15",
        "sex": "MALE",
        "bmi": 28.5,
        "diabetesOnsetDate": "2015-03-01",
        "hba1c": 7.2,
        "fastingGlucose": 6.1,
    }


class WearableEventUser(HttpUser):
    """
    Simulates a wearable device sending one event per minute per patient.
    Measures end-to-end latency: POST wearable event -> poll until prediction COMPLETED.
    """
    wait_time = between(55, 65)  # ~1 event per minute per user

    def on_start(self) -> None:
        """Register patient and upload EHR before generating events."""
        self.patient_id = PATIENT_IDS[self.user_id % len(PATIENT_IDS)]
        self.client.post(
            f"/api/v1/patients/{self.patient_id}/ehr",
            json=make_ehr_payload(),
            headers=DEV_AUTH_HEADER,
            name="/api/v1/patients/[id]/ehr (setup)",
        )

    @task
    def send_wearable_event_and_wait_for_prediction(self) -> None:
        """
        POST wearable event, then poll GET /predictions until COMPLETED.
        Records total elapsed time as the end-to-end latency.
        """
        start = time.time()

        # POST wearable event
        post_response = self.client.post(
            f"/api/v1/patients/{self.patient_id}/wearable-events",
            json=make_wearable_payload(),
            headers=DEV_AUTH_HEADER,
            name="/api/v1/patients/[id]/wearable-events",
        )

        if post_response.status_code != 202:
            return

        # Also trigger an on-demand prediction (for REQ-030 measurement)
        pred_response = self.client.post(
            f"/api/v1/patients/{self.patient_id}/predictions",
            json={},
            headers=DEV_AUTH_HEADER,
            name="/api/v1/patients/[id]/predictions (trigger)",
        )

        if pred_response.status_code != 202:
            return

        prediction_id = pred_response.json().get("predictionId")
        if not prediction_id:
            return

        # Poll until COMPLETED or timeout
        deadline = start + 10.0  # 10s polling timeout
        completed = False
        while time.time() < deadline:
            poll = self.client.get(
                f"/api/v1/predictions/{prediction_id}",
                headers=DEV_AUTH_HEADER,
                name="/api/v1/predictions/[id] (poll)",
            )
            if poll.status_code == 200:
                status = poll.json().get("status")
                if status in ("COMPLETED", "FAILED"):
                    completed = True
                    break
            time.sleep(0.2)

        elapsed_ms = (time.time() - start) * 1000

        # Record the custom metric (Locust fires this as a request event)
        events.request.fire(
            request_type="CUSTOM",
            name="end_to_end_prediction_latency",
            response_time=elapsed_ms,
            response_length=0,
            exception=None if completed else TimeoutError("Prediction did not complete in time"),
            context={},
        )


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs: Any) -> None:  # type: ignore[override]
    """Write summary results to JSON for CI artefact capture."""
    stats = environment.stats
    entry = stats.get("CUSTOM", "end_to_end_prediction_latency")
    if entry:
        result = {
            "metric": "end_to_end_prediction_latency_ms",
            "num_requests": entry.num_requests,
            "num_failures": entry.num_failures,
            "avg_response_time_ms": entry.avg_response_time,
            "p50_ms": entry.get_response_time_percentile(0.50),
            "p95_ms": entry.get_response_time_percentile(0.95),
            "p99_ms": entry.get_response_time_percentile(0.99),
            "sla_met": entry.get_response_time_percentile(0.95) <= 3000,
        }
        with open("load_test_results.json", "w") as f:
            json.dump(result, f, indent=2)
        print(f"\nLoad test results: {result}")
        if not result["sla_met"]:
            print("WARNING: p95 latency exceeded 3000ms SLA target")
