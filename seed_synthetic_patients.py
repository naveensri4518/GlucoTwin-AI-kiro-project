#!/usr/bin/env python3
"""
GlucoTwin AI — Idempotent Synthetic Research Patient Seeder.

Populates PostgreSQL with 12 diverse, clinically plausible synthetic research patients
via the official Spring Boot REST APIs:
  - Ingests EHR record (DOB, sex, BMI, onset, HbA1c, fasting glucose)
  - Ingests chronological wearable telemetry (glucose, HR, HRV, sleep, steps, activity)
  - Triggers initial prediction on select patients

All records are marked with deterministic UUIDs and clinical ranges.
Idempotent: Re-running this script updates existing twins without creating duplicate patient records.
"""

import json
import urllib.request
import urllib.error
import datetime
from datetime import timezone, timedelta

BACKEND_URL = "http://localhost:8080"
HEADERS = {
    "Content-Type": "application/json",
    "X-Dev-Auth": "ROLE_CLINICIAN",
}

# 12 diverse, clinically plausible synthetic patient profiles
SYNTHETIC_PATIENTS = [
    {
        "id": "e1000000-0000-0000-0000-000000000001",
        "label": "SYNTH-001 · Type 2 Diabetes, High Risk",
        "ehr": {
            "dateOfBirth": "1968-04-12",
            "sex": "MALE",
            "bmi": 32.4,
            "diabetesOnsetDate": "2015-06-20",
            "hba1c": 8.9,
            "fastingGlucose": 9.4,
        },
        "wearables": [
            {"glucose": 9.8, "hr": 78, "hrv": 28, "sleep": 5.8, "stage": "LIGHT", "steps": 3200, "act": "SEDENTARY", "minutes_ago": 60},
            {"glucose": 10.4, "hr": 82, "hrv": 25, "sleep": 5.8, "stage": "LIGHT", "steps": 3800, "act": "LIGHT", "minutes_ago": 30},
            {"glucose": 11.2, "hr": 86, "hrv": 22, "sleep": 5.8, "stage": "LIGHT", "steps": 4100, "act": "SEDENTARY", "minutes_ago": 5},
        ],
        "trigger_prediction": True,
    },
    {
        "id": "e1000000-0000-0000-0000-000000000002",
        "label": "SYNTH-002 · Type 2 Diabetes, Well-Controlled",
        "ehr": {
            "dateOfBirth": "1982-11-03",
            "sex": "FEMALE",
            "bmi": 24.8,
            "diabetesOnsetDate": "2020-01-15",
            "hba1c": 6.2,
            "fastingGlucose": 5.6,
        },
        "wearables": [
            {"glucose": 5.8, "hr": 64, "hrv": 52, "sleep": 7.6, "stage": "DEEP", "steps": 8400, "act": "MODERATE", "minutes_ago": 60},
            {"glucose": 6.1, "hr": 68, "hrv": 48, "sleep": 7.6, "stage": "DEEP", "steps": 9200, "act": "MODERATE", "minutes_ago": 30},
            {"glucose": 5.9, "hr": 66, "hrv": 50, "sleep": 7.6, "stage": "DEEP", "steps": 9800, "act": "LIGHT", "minutes_ago": 5},
        ],
        "trigger_prediction": True,
    },
    {
        "id": "e1000000-0000-0000-0000-000000000003",
        "label": "SYNTH-003 · Pre-Diabetes, Moderate Variability",
        "ehr": {
            "dateOfBirth": "1975-08-25",
            "sex": "FEMALE",
            "bmi": 28.1,
            "diabetesOnsetDate": "2023-03-10",
            "hba1c": 6.8,
            "fastingGlucose": 6.4,
        },
        "wearables": [
            {"glucose": 7.1, "hr": 72, "hrv": 38, "sleep": 6.9, "stage": "LIGHT", "steps": 6200, "act": "LIGHT", "minutes_ago": 45},
            {"glucose": 7.6, "hr": 75, "hrv": 35, "sleep": 6.9, "stage": "LIGHT", "steps": 6800, "act": "MODERATE", "minutes_ago": 15},
        ],
        "trigger_prediction": True,
    },
    {
        "id": "e1000000-0000-0000-0000-000000000004",
        "label": "SYNTH-004 · Post-Bariatric, Low Fasting",
        "ehr": {
            "dateOfBirth": "1988-02-14",
            "sex": "MALE",
            "bmi": 26.3,
            "diabetesOnsetDate": "2018-10-05",
            "hba1c": 5.9,
            "fastingGlucose": 4.9,
        },
        "wearables": [
            {"glucose": 5.2, "hr": 62, "hrv": 58, "sleep": 8.0, "stage": "DEEP", "steps": 11200, "act": "ACTIVE", "minutes_ago": 40},
            {"glucose": 5.4, "hr": 65, "hrv": 55, "sleep": 8.0, "stage": "DEEP", "steps": 12100, "act": "MODERATE", "minutes_ago": 10},
        ],
        "trigger_prediction": False,
    },
    {
        "id": "e1000000-0000-0000-0000-000000000005",
        "label": "SYNTH-005 · Elderly Patient, Nocturnal Hypo Risk",
        "ehr": {
            "dateOfBirth": "1949-09-18",
            "sex": "FEMALE",
            "bmi": 23.5,
            "diabetesOnsetDate": "2008-05-12",
            "hba1c": 7.1,
            "fastingGlucose": 5.1,
        },
        "wearables": [
            {"glucose": 4.6, "hr": 58, "hrv": 32, "sleep": 6.1, "stage": "REM", "steps": 2800, "act": "SEDENTARY", "minutes_ago": 30},
            {"glucose": 4.3, "hr": 56, "hrv": 30, "sleep": 6.1, "stage": "REM", "steps": 2900, "act": "SEDENTARY", "minutes_ago": 5},
        ],
        "trigger_prediction": True,
    },
    {
        "id": "e1000000-0000-0000-0000-000000000006",
        "label": "SYNTH-006 · Young Adult, Glycemic Spikes",
        "ehr": {
            "dateOfBirth": "1997-12-01",
            "sex": "MALE",
            "bmi": 25.1,
            "diabetesOnsetDate": "2021-07-22",
            "hba1c": 7.8,
            "fastingGlucose": 7.2,
        },
        "wearables": [
            {"glucose": 8.4, "hr": 76, "hrv": 45, "sleep": 6.8, "stage": "LIGHT", "steps": 7500, "act": "MODERATE", "minutes_ago": 50},
            {"glucose": 10.1, "hr": 84, "hrv": 39, "sleep": 6.8, "stage": "LIGHT", "steps": 8100, "act": "MODERATE", "minutes_ago": 15},
        ],
        "trigger_prediction": True,
    },
    {
        "id": "e1000000-0000-0000-0000-000000000007",
        "label": "SYNTH-007 · Metabolic Syndrome, High BMI",
        "ehr": {
            "dateOfBirth": "1972-06-30",
            "sex": "MALE",
            "bmi": 36.2,
            "diabetesOnsetDate": "2016-11-19",
            "hba1c": 8.4,
            "fastingGlucose": 8.8,
        },
        "wearables": [
            {"glucose": 9.2, "hr": 80, "hrv": 24, "sleep": 5.4, "stage": "LIGHT", "steps": 2400, "act": "SEDENTARY", "minutes_ago": 45},
            {"glucose": 9.7, "hr": 83, "hrv": 22, "sleep": 5.4, "stage": "LIGHT", "steps": 2700, "act": "SEDENTARY", "minutes_ago": 10},
        ],
        "trigger_prediction": True,
    },
    {
        "id": "e1000000-0000-0000-0000-000000000008",
        "label": "SYNTH-008 · Athletic Baseline, Active Lifestyle",
        "ehr": {
            "dateOfBirth": "1991-03-14",
            "sex": "FEMALE",
            "bmi": 21.7,
            "diabetesOnsetDate": "2022-09-08",
            "hba1c": 5.7,
            "fastingGlucose": 5.2,
        },
        "wearables": [
            {"glucose": 5.1, "hr": 54, "hrv": 68, "sleep": 8.2, "stage": "DEEP", "steps": 14500, "act": "VIGOROUS", "minutes_ago": 60},
            {"glucose": 5.3, "hr": 58, "hrv": 64, "sleep": 8.2, "stage": "DEEP", "steps": 15200, "act": "MODERATE", "minutes_ago": 15},
        ],
        "trigger_prediction": False,
    },
    {
        "id": "e1000000-0000-0000-0000-000000000009",
        "label": "SYNTH-009 · Shift Worker, Disrupted Circadian",
        "ehr": {
            "dateOfBirth": "1985-10-11",
            "sex": "MALE",
            "bmi": 29.5,
            "diabetesOnsetDate": "2019-04-17",
            "hba1c": 7.5,
            "fastingGlucose": 7.0,
        },
        "wearables": [
            {"glucose": 7.8, "hr": 74, "hrv": 36, "sleep": 4.8, "stage": "LIGHT", "steps": 5100, "act": "LIGHT", "minutes_ago": 40},
            {"glucose": 8.5, "hr": 79, "hrv": 31, "sleep": 4.8, "stage": "LIGHT", "steps": 5600, "act": "LIGHT", "minutes_ago": 10},
        ],
        "trigger_prediction": True,
    },
    {
        "id": "e1000000-0000-0000-0000-000000000010",
        "label": "SYNTH-010 · Newly Diagnosed, Initial Standby",
        "ehr": {
            "dateOfBirth": "1980-01-20",
            "sex": "FEMALE",
            "bmi": 27.0,
            "diabetesOnsetDate": "2026-08-01",
            "hba1c": 7.0,
            "fastingGlucose": 6.7,
        },
        "wearables": [], # Standby initialised twin — no wearables yet
        "trigger_prediction": False,
    },
    {
        "id": "e1000000-0000-0000-0000-000000000011",
        "label": "SYNTH-011 · Gestational Risk Profile",
        "ehr": {
            "dateOfBirth": "1994-07-09",
            "sex": "FEMALE",
            "bmi": 26.8,
            "diabetesOnsetDate": "2025-11-14",
            "hba1c": 6.4,
            "fastingGlucose": 5.9,
        },
        "wearables": [
            {"glucose": 6.5, "hr": 75, "hrv": 44, "sleep": 7.4, "stage": "LIGHT", "steps": 6100, "act": "LIGHT", "minutes_ago": 30},
            {"glucose": 6.8, "hr": 78, "hrv": 41, "sleep": 7.4, "stage": "LIGHT", "steps": 6500, "act": "LIGHT", "minutes_ago": 5},
        ],
        "trigger_prediction": True,
    },
    {
        "id": "e1000000-0000-0000-0000-000000000012",
        "label": "SYNTH-012 · High Glycemic Lability",
        "ehr": {
            "dateOfBirth": "1965-05-04",
            "sex": "MALE",
            "bmi": 30.8,
            "diabetesOnsetDate": "2012-08-29",
            "hba1c": 8.7,
            "fastingGlucose": 9.1,
        },
        "wearables": [
            {"glucose": 8.9, "hr": 81, "hrv": 26, "sleep": 5.6, "stage": "LIGHT", "steps": 3900, "act": "SEDENTARY", "minutes_ago": 60},
            {"glucose": 11.5, "hr": 88, "hrv": 21, "sleep": 5.6, "stage": "LIGHT", "steps": 4400, "act": "LIGHT", "minutes_ago": 25},
            {"glucose": 12.1, "hr": 92, "hrv": 19, "sleep": 5.6, "stage": "LIGHT", "steps": 4600, "act": "SEDENTARY", "minutes_ago": 5},
        ],
        "trigger_prediction": True,
    },
]

def make_request(method, path, body=None):
    url = f"{BACKEND_URL}{path}"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, headers=HEADERS, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            content = resp.read().decode("utf-8")
            return resp.status, json.loads(content) if content else {}
    except urllib.error.HTTPError as e:
        err_content = e.read().decode("utf-8")
        return e.code, {"error": err_content}
    except Exception as e:
        return 500, {"error": str(e)}

def seed():
    print(f"[*] Connecting to GlucoTwin AI Backend at {BACKEND_URL}...")
    code, health = make_request("GET", "/api/health")
    if code != 200 or health.get("status") != "UP":
        print(f"[!] Backend not ready: status={code}, body={health}")
        return False

    print(f"[+] Backend is online: {health.get('service')} ({health.get('status')})")

    now = datetime.datetime.now(timezone.utc)
    seeded_count = 0

    for patient in SYNTHETIC_PATIENTS:
        pid = patient["id"]
        label = patient["label"]
        print(f"\n---> Processing {label} [{pid}]")

        # 1. Ingest EHR (creates patient + initializes twin if not exists)
        code, resp = make_request("POST", f"/api/v1/patients/{pid}/ehr", patient["ehr"])
        if code in (200, 201):
            print(f"     [+] EHR ingested: status={resp.get('status')}")
        else:
            print(f"     [!] Failed to ingest EHR: HTTP {code} - {resp}")
            continue

        # 2. Ingest Wearables
        for w in patient.get("wearables", []):
            event_ts = (now - timedelta(minutes=w["minutes_ago"])).isoformat()
            payload = {
                "glucoseReading": w["glucose"],
                "heartRate": float(w["hr"]),
                "hrv": float(w["hrv"]),
                "sleepDuration": float(w["sleep"]),
                "sleepStage": w["stage"],
                "stepCount": int(w["steps"]),
                "activityLevel": w["act"],
                "eventTimestamp": event_ts,
            }
            w_code, w_resp = make_request("POST", f"/api/v1/patients/{pid}/wearable-events", payload)
            if w_code in (200, 201, 202):
                print(f"     [+] Wearable telemetry ingested (glucose={w['glucose']} mmol/L, t=-{w['minutes_ago']}m)")
            else:
                print(f"     [-] Wearable ingestion note: HTTP {w_code}")

        # 3. Trigger Prediction if requested
        if patient.get("trigger_prediction") and len(patient.get("wearables", [])) > 0:
            p_code, p_resp = make_request("POST", f"/api/v1/patients/{pid}/predictions")
            if p_code in (200, 201, 202):
                print(f"     [+] ML Prediction executed (id={p_resp.get('predictionId')})")
            else:
                print(f"     [-] Prediction status: HTTP {p_code}")

        seeded_count += 1

    # Final query verification
    q_code, q_resp = make_request("GET", "/api/v1/patients?size=50")
    total = q_resp.get("totalElements", 0)
    print(f"\n=======================================================")
    print(f"[OK] Seeding completed successfully.")
    print(f"[OK] Total registered patients now in PostgreSQL: {total}")
    print(f"=======================================================")
    return True

if __name__ == "__main__":
    seed()
