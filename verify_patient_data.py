import urllib.request
import json
import uuid

BASE_URL = "http://localhost:8080/api/v1"
HEADERS = {
    "X-Dev-Auth": "ROLE_CLINICIAN",
    "Content-Type": "application/json"
}

def verify_patient_data():
    print("--- 1. Querying Patient List ---")
    req = urllib.request.Request(f"{BASE_URL}/patients?page=0&size=50", headers=HEADERS)
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode())
    
    total = data.get("totalElements", 0)
    patients = data.get("content", [])
    print(f"Total registered patients: {total}")
    print(f"Retrieved in page: {len(patients)}")
    assert total >= 13, f"Expected at least 13 patients, got {total}"
    
    # Check synthetic vs clinical labelling
    synthetic_count = sum(1 for p in patients if p["patientId"].startswith("e1000000"))
    enrolled_count = total - synthetic_count
    print(f"Synthetic research records: {synthetic_count}")
    print(f"Enrolled/Clinical records: {enrolled_count}")
    
    # 2. Test enrolling a new real patient
    new_id = str(uuid.uuid4())
    print(f"\n--- 2. Enrolling New Patient: {new_id} ---")
    enroll_payload = {
        "dateOfBirth": "1988-11-20",
        "sex": "FEMALE",
        "bmi": 24.8,
        "diabetesOnsetDate": "2021-04-12",
        "hba1c": 6.9,
        "fastingGlucose": 6.2
    }
    req = urllib.request.Request(
        f"{BASE_URL}/patients/{new_id}/ehr",
        data=json.dumps(enroll_payload).encode(),
        headers=HEADERS,
        method="POST"
    )
    with urllib.request.urlopen(req) as resp:
        res = json.loads(resp.read().decode())
        print(f"EHR Ingest Status: {resp.status}, BMI: {res.get('bmi')}")

    # Verify patient now in list
    req = urllib.request.Request(f"{BASE_URL}/patients?page=0&size=50", headers=HEADERS)
    with urllib.request.urlopen(req) as resp:
        data_after = json.loads(resp.read().decode())
    new_total = data_after.get("totalElements", 0)
    print(f"New total patient count after enrollment: {new_total} (was {total})")
    assert new_total == total + 1, "Expected total count to increment by 1"

    # Verify independent twin state for newly enrolled patient
    req = urllib.request.Request(f"{BASE_URL}/patients/{new_id}/twin-state", headers=HEADERS)
    with urllib.request.urlopen(req) as resp:
        twin = json.loads(resp.read().decode())
        print(f"New Patient Twin Status: {twin.get('status')}, Version: {twin.get('version')}")

    # Verify independent records across patients
    print("\n--- 3. Verifying Independent Patient Data Isolation ---")
    p1 = patients[0]["patientId"]
    p2 = patients[1]["patientId"]
    req1 = urllib.request.Request(f"{BASE_URL}/patients/{p1}/twin-state", headers=HEADERS)
    with urllib.request.urlopen(req1) as r1:
        t1 = json.loads(r1.read().decode())
    req2 = urllib.request.Request(f"{BASE_URL}/patients/{p2}/twin-state", headers=HEADERS)
    with urllib.request.urlopen(req2) as r2:
        t2 = json.loads(r2.read().decode())
    
    print(f"Patient 1 ({p1[:8]}...): Twin version={t1.get('version')}, glucose={t1.get('currentGlucoseMmol')}")
    print(f"Patient 2 ({p2[:8]}...): Twin version={t2.get('version')}, glucose={t2.get('currentGlucoseMmol')}")
    assert t1.get("patientId") == p1, "Twin state must match queried patient 1"
    assert t2.get("patientId") == p2, "Twin state must match queried patient 2"

if __name__ == "__main__":
    verify_patient_data()
