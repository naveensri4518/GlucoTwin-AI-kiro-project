# GlucoTwin AI — Data Dictionary

## Overview
This document defines all data fields across domain entities, APIs, and the ML feature vector.

## Entities

### Patient
| Field | Type | Description |
|---|---|---|
| patientId | UUID | Unique patient identifier |
| createdAt | Timestamp | Record creation time |

### DigitalTwinState
| Field | Type | Description |
|---|---|---|
| patientId | UUID | Links to patient |
| twinVersion | Integer | Monotonically increasing mutation counter |
| status | Enum | INITIALISED / ACTIVE / STALE / ARCHIVED |
| lastUpdatedAt | Timestamp | Last mutation time |

### StaticLayer (EHR)
| Field | Type | Constraints | Provenance |
|---|---|---|---|
| dateOfBirth | Date | Past date, required | OBSERVED |
| sex | Enum | MALE/FEMALE/OTHER, required | OBSERVED |
| bmi | Float | 10.0–80.0 kg/m², optional | OBSERVED |
| diabetesOnsetDate | Date | Past date, required | OBSERVED |
| hba1c | Float | 3.0–20.0 %, optional | OBSERVED |
| fastingGlucose | Float | 1.0–35.0 mmol/L, optional | OBSERVED |

### DynamicLayer (Wearable)
| Field | Type | Constraints | Provenance |
|---|---|---|---|
| glucoseReading | Float | 1.0–35.0 mmol/L, required for prediction | OBSERVED |
| heartRate | Float | 20–300 bpm, optional | OBSERVED |
| hrv | Float | 0–300 ms, optional | OBSERVED |
| sleepDuration | Float | 0–24 hours, optional | OBSERVED |
| sleepStage | Enum | AWAKE/LIGHT/DEEP/REM, optional | OBSERVED |
| stepCount | Integer | 0–100,000, optional | OBSERVED |
| activityLevel | Enum | SEDENTARY/LIGHT/MODERATE/VIGOROUS, optional | OBSERVED |
| eventTimestamp | Timestamp | Not future, not >24h past | OBSERVED |

## ML Feature Vector (v1)
| Feature Name | Source | Computation |
|---|---|---|
| cgm_current | dynamicLayer.glucoseReading | Direct value |
| cgm_delta_30m | cgmHistory | Latest - reading 30min ago |
| cgm_mean_60m | cgmHistory | Rolling mean over 60min window |
| cgm_slope_60m | cgmHistory | Linear regression slope over 60min |
| heart_rate_current | dynamicLayer.heartRate | Direct value (imputed if null) |
| hrv_current | dynamicLayer.hrv | Direct value (imputed if null) |
| sleep_duration_last | dynamicLayer.sleepDuration | Direct value (imputed if null) |
| step_count_today | dynamicLayer.stepCount | Direct value (imputed if null) |
| activity_level_encoded | dynamicLayer.activityLevel | SEDENTARY=0, LIGHT=1, MODERATE=2, VIGOROUS=3 |
| hba1c_latest | staticLayer.hba1c | Most recent value (imputed if null) |
| bmi | staticLayer.bmi | Direct value (imputed if null) |
| time_of_day_sin | eventTimestamp.hour | sin(2π × hour / 24) |
| time_of_day_cos | eventTimestamp.hour | cos(2π × hour / 24) |
| days_since_diagnosis | staticLayer.diabetesOnsetDate | (today - onsetDate).days |

## Confidence Interval Method
v1 uses split conformal prediction. Calibration scores are computed once on a held-out validation set
during model training and stored in calibration_scores.npy. At inference, the nonconformity score
for the prediction is computed and the 95th percentile of calibration scores defines the interval width.
Coverage guarantee: ≥ 95% of true outcomes fall within the returned interval.

## Data Provenance Labels
- OBSERVED: actual measured/recorded data from EHR or wearable
- PREDICTED: model-generated probabilistic output
- SIMULATED: hypothetical what-if scenario data (reserved for future)

## Risk Category Thresholds (default, configurable)
- LOW: spikeProbability < 0.30
- MODERATE: 0.30 ≤ spikeProbability < 0.60
- HIGH: 0.60 ≤ spikeProbability < 0.85
- CRITICAL: spikeProbability ≥ 0.85
Boundary values fall into the upper category.
