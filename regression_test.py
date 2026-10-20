import json
import urllib.request
import urllib.error
import time
from datetime import datetime, timezone, timedelta

BASE_URL = "http://localhost:8080"
HEADERS = {
    "Content-Type": "application/json",
    "X-Dev-Auth": "ROLE_CLINICIAN"
}

def req(method, path, data=None):
    url = f"{BASE_URL}{path}"
    body = json.dumps(data).encode("utf-8") if data else None
    request = urllib.request.Request(url, data=body, headers=HEADERS, method=method)
    try:
        with urllib.request.urlopen(request) as resp:
            content = resp.read().decode("utf-8")
            try:
                return resp.status, json.loads(content)
            except Exception:
                return resp.status, content
    except urllib.error.HTTPError as e:
        content = e.read().decode("utf-8")
        try:
            return e.code, json.loads(content)
        except Exception:
            return e.code, content

PID = "00000000-0000-0000-0000-000000000001"

print("==================================================")
print("CHECK 5: PATIENT INGESTION, PERSISTENCE, LISTING, DETAIL")
print("==================================================")
ehr_data = {
    "dateOfBirth": "1980-05-15",
    "sex": "MALE",
    "bmi": 27.5,
    "diabetesOnsetDate": "2018-09-01",
    "hba1c": 7.4,
    "fastingGlucose": 6.8
}
status, res = req("POST", f"/api/v1/patients/{PID}/ehr", ehr_data)
print(f"5.1 POST EHR -> Status: {status}, Response: {res}")
assert status == 201 or status == 200

status, res = req("GET", "/api/v1/patients")
print(f"5.2 GET /patients (Listing) -> Status: {status}, Total: {res.get('totalElements')}")
assert status == 200 and res.get('totalElements') >= 1

status, res = req("GET", f"/api/v1/patients/{PID}")
print(f"5.3 GET /patients/{{id}} (Detail) -> Status: {status}, PatientId: {res.get('patientId')}")
assert status == 200 and res.get('patientId') == PID

status, res = req("GET", f"/api/v1/patients/{PID}/ehr")
print(f"5.4 GET /patients/{{id}}/ehr -> Status: {status}, HbA1c: {res.get('hba1c')}, BMI: {res.get('bmi')}")
assert status == 200 and res.get('bmi') == 27.5

print("\n==================================================")
print("CHECK 9: PUBLISH WEARABLE EVENT & ACTUAL CONSUMER PROCESSING")
print("==================================================")
event_time = (datetime.now(timezone.utc) - timedelta(minutes=5)).isoformat()
wearable_data = {
    "glucoseReading": 9.2,
    "heartRate": 78.0,
    "hrv": 38.0,
    "sleepDuration": 6.8,
    "sleepStage": "LIGHT",
    "stepCount": 5100,
    "activityLevel": "MODERATE",
    "eventTimestamp": event_time
}
status, res = req("POST", f"/api/v1/patients/{PID}/wearable-events", wearable_data)
print(f"9.1 POST Wearable Event -> Status: {status}, EventId: {res.get('eventId')}, Status: {res.get('status')}")
assert status == 202

# Give stream consumer a brief moment to process
time.sleep(1.0)

status, twin_res = req("GET", f"/api/v1/patients/{PID}/twin-state")
print(f"9.2 Twin State After Event -> Status: {status}, Twin Status: {twin_res.get('status')}, Glucose: {twin_res.get('dynamicLayer', {}).get('glucoseReading')}, Version: {twin_res.get('twinVersion')}")
assert status == 200 and twin_res.get('status') == 'ACTIVE' and twin_res.get('dynamicLayer', {}).get('glucoseReading') == 9.2

status, cgm_res = req("GET", f"/api/v1/patients/{PID}/wearable-events/cgm-history")
print(f"9.3 Rolling CGM History Buffer -> Status: {status}, Readings Count: {len(cgm_res)}")
assert status == 200 and len(cgm_res) >= 1

print("\n==================================================")
print("CHECK 6 & 7: REAL BACKEND-TO-ML PREDICTION, HISTORY & SHAP")
print("==================================================")
status, pred_init = req("POST", f"/api/v1/patients/{PID}/predictions")
print(f"6.1 POST /predictions -> Status: {status}, PredictionId: {pred_init.get('predictionId')}")
assert status == 202
pred_id = pred_init.get("predictionId")

status, pred_detail = req("GET", f"/api/v1/predictions/{pred_id}")
print(f"6.2 GET /predictions/{{id}} -> Status: {status}")
print(f"    Spike Probability: {pred_detail.get('spikeProbability')}")
print(f"    Risk Category: {pred_detail.get('riskCategory')}")
print(f"    Model Version: {pred_detail.get('modelVersion')}")
print(f"    Data Provenance: {pred_detail.get('dataProvenance')}")
assert status == 200 and pred_detail.get('spikeProbability') is not None
assert pred_detail.get('dataProvenance') == 'PREDICTED'

factors = pred_detail.get('topContributingFactors', [])
print(f"7.1 SHAP Explanations -> Count: {len(factors)}")
for f in factors:
    print(f"    Factor: {f.get('factorName')}, Contribution: {f.get('contribution')}, Direction: {f.get('direction')}")
assert len(factors) >= 1

status, hist = req("GET", f"/api/v1/patients/{PID}/predictions")
print(f"7.2 Prediction History -> Status: {status}, Total Elements: {hist.get('totalElements')}")
assert status == 200 and hist.get('totalElements') >= 1

print("\n==================================================")
print("CHECK 8: CLINICAL INSIGHTS & WHAT-IF SIMULATION")
print("==================================================")
insight_payload = {"question": "Assess acute risk profile following meal"}
status, insight = req("POST", f"/api/v1/patients/{PID}/clinical-insights", insight_payload)
print(f"8.1 Clinical Insights -> Status: {status}")
print(f"    Provenance: {insight.get('dataProvenance')}")
print(f"    Summary: {insight.get('evidenceSummary')[:120]}...")
print(f"    Clinical KB Count: {len(insight.get('clinicalKnowledgeEvidence', []))}")
print(f"    Execution Steps: {len(insight.get('executionTrace', {}).get('steps', []))}")
assert status == 200 and insight.get('dataProvenance') == 'OBSERVED+PREDICTED'

sim_payload = {
    "mealCarbsGrams": 60.0,
    "activityLevel": "LIGHT",
    "medicationTaken": True
}
status, sim = req("POST", f"/api/v1/patients/{PID}/simulations", sim_payload)
print(f"8.2 What-If Simulation -> Status: {status}")
print(f"    SimulationId: {sim.get('simulationId')}")
print(f"    Simulated Spike Prob: {sim.get('spikeProbability')}")
print(f"    Provenance: {sim.get('dataProvenance')}")
print(f"    Delta vs Baseline: {sim.get('deltaVsBaseline')}")
print(f"    Disclaimer: {sim.get('disclaimer')}")
assert status == 200 and sim.get('dataProvenance') == 'SIMULATED'

print("\n==================================================")
print("CHECK 10: CONFIRM SIMULATED DATA NEVER MODIFIES OBSERVED DATA")
print("==================================================")
status, twin_after_sim = req("GET", f"/api/v1/patients/{PID}/twin-state")
observed_glucose = twin_after_sim.get('dynamicLayer', {}).get('glucoseReading')
observed_provenance = twin_after_sim.get('dynamicLayer', {}).get('dataProvenance')
print(f"10.1 Real Twin State after Simulation -> Glucose: {observed_glucose}, Provenance: {observed_provenance}")
assert observed_glucose == 9.2, f"Expected 9.2, got {observed_glucose}"
assert observed_provenance == 'OBSERVED', f"Expected OBSERVED, got {observed_provenance}"

status, ehr_after_sim = req("GET", f"/api/v1/patients/{PID}/ehr")
print(f"10.2 Real EHR after Simulation -> HbA1c: {ehr_after_sim.get('hba1c')}, BMI: {ehr_after_sim.get('bmi')}")
assert ehr_after_sim.get('bmi') == 27.5
print(">>> ISOLATION VERIFIED: Simulated scenarios do NOT alter real DigitalTwinState or EHR data.")

print("\nALL BACKEND & INTEGRATION CHECKS PASSED SUCCESSFULLY!")
