import json
import urllib.request
import urllib.error
from datetime import datetime, timezone, timedelta

BASE_URL = "http://localhost:8080"
HEADERS = {
    "Content-Type": "application/json",
    "X-Dev-Auth": "ROLE_CLINICIAN"
}

def request(method, path, data=None):
    url = f"{BASE_URL}{path}"
    body = json.dumps(data).encode("utf-8") if data else None
    req = urllib.request.Request(url, data=body, headers=HEADERS, method=method)
    try:
        with urllib.request.urlopen(req) as response:
            status = response.status
            content = response.read().decode("utf-8")
            try:
                res_json = json.loads(content)
            except Exception:
                res_json = content
            return status, res_json
    except urllib.error.HTTPError as e:
        content = e.read().decode("utf-8")
        try:
            res_json = json.loads(content)
        except Exception:
            res_json = content
        return e.code, res_json

PID = "00000000-0000-0000-0000-000000000001"

print("==================================================")
print("1. Ingesting EHR for Patient (Workflow A)")
print("==================================================")
ehr_payload = {
    "dateOfBirth": "1980-05-15",
    "sex": "MALE",
    "bmi": 27.5,
    "diabetesOnsetDate": "2018-09-01",
    "hba1c": 7.4,
    "fastingGlucose": 6.8
}
status, res = request("POST", f"/api/v1/patients/{PID}/ehr", ehr_payload)
print(f"Status: {status}, Response: {res}")

print("\n==================================================")
print("2. Retrieving Patient List & Details (Workflow A)")
print("==================================================")
status, res = request("GET", "/api/v1/patients")
print(f"List Patients -> Status: {status}, Total Elements: {res.get('totalElements') if isinstance(res, dict) else res}")

status, res = request("GET", f"/api/v1/patients/{PID}")
print(f"Get Patient Detail -> Status: {status}, Patient: {res}")

status, res = request("GET", f"/api/v1/patients/{PID}/ehr")
print(f"Get Patient EHR -> Status: {status}, BMI: {res.get('bmi') if isinstance(res, dict) else res}")

print("\n==================================================")
print("3. Ingesting Wearable Sensor Event (Workflow E)")
print("==================================================")
# 10 minutes ago
event_time = (datetime.now(timezone.utc) - timedelta(minutes=10)).isoformat()
wearable_payload = {
    "glucoseReading": 8.7,
    "heartRate": 74.0,
    "hrv": 42.0,
    "sleepDuration": 7.2,
    "sleepStage": "LIGHT",
    "stepCount": 4500,
    "activityLevel": "MODERATE",
    "eventTimestamp": event_time
}
status, res = request("POST", f"/api/v1/patients/{PID}/wearable-events", wearable_payload)
print(f"Wearable Ingest -> Status: {status}, Response: {res}")

print("\n==================================================")
print("4. Inspecting Digital Twin & Rolling CGM History")
print("==================================================")
status, res = request("GET", f"/api/v1/patients/{PID}/twin-state")
print(f"Twin State -> Status: {status}, Version: {res.get('twinVersion')}, Status: {res.get('status')}, Glucose: {res.get('dynamicLayer', {}).get('glucoseReading')}")

status, res = request("GET", f"/api/v1/patients/{PID}/wearable-events/cgm-history")
print(f"CGM History -> Status: {status}, Count: {len(res) if isinstance(res, list) else res}")

print("\n==================================================")
print("5. Triggering Glucose Spike Prediction (Workflow B)")
print("==================================================")
status, res = request("POST", f"/api/v1/patients/{PID}/predictions")
print(f"Trigger Prediction -> Status: {status}, Response: {res}")
prediction_id = res.get("predictionId") if isinstance(res, dict) else None

if prediction_id:
    print(f"\nFetching Prediction Record: {prediction_id}")
    status, pred = request("GET", f"/api/v1/predictions/{prediction_id}")
    print(f"Status: {status}, Spike Prob: {pred.get('spikeProbability')}, Risk: {pred.get('riskCategory')}, Factors: {len(pred.get('topContributingFactors', []))}")

status, res = request("GET", f"/api/v1/patients/{PID}/predictions")
print(f"Prediction History -> Status: {status}, Total Elements: {res.get('totalElements') if isinstance(res, dict) else res}")

print("\n==================================================")
print("6. Generating Clinical Insights (Workflow C)")
print("==================================================")
insight_payload = {
    "question": "What is the patient risk profile based on current sensor readings?"
}
status, res = request("POST", f"/api/v1/patients/{PID}/clinical-insights", insight_payload)
print(f"Clinical Insights -> Status: {status}, Summary: {res.get('summary') if isinstance(res, dict) else res}")
if isinstance(res, dict):
    print(f"Provenance: {res.get('provenance')}, Fallback: {res.get('explanationFallback')}")

print("\n==================================================")
print("7. Running What-If Simulation (Workflow D)")
print("==================================================")
sim_payload = {
    "mealCarbsGrams": 45.0,
    "activityLevel": "MODERATE",
    "medicationTaken": True
}
status, res = request("POST", f"/api/v1/patients/{PID}/simulations", sim_payload)
print(f"What-If Simulation -> Status: {status}")
if isinstance(res, dict):
    print(f"Baseline Prob: {res.get('baselineSpikeProbability')}, Simulated Prob: {res.get('simulatedSpikeProbability')}, Provenance: {res.get('dataProvenance')}")
    print(f"Disclaimer: {res.get('disclaimer')}")
