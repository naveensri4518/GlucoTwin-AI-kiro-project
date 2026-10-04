-- Synthetic patient seed data
-- ALL records are purely synthetic. No real patient information.
-- data_source = 'SYNTHETIC' on all records.

INSERT INTO patients (patient_id, created_at, updated_at) VALUES
    ('a1000000-0000-0000-0000-000000000001', now(), now()),
    ('a1000000-0000-0000-0000-000000000002', now(), now()),
    ('a1000000-0000-0000-0000-000000000003', now(), now()),
    ('a1000000-0000-0000-0000-000000000004', now(), now()),
    ('a1000000-0000-0000-0000-000000000005', now(), now())
ON CONFLICT (patient_id) DO NOTHING;

INSERT INTO patient_ehr (ehr_id, patient_id, date_of_birth, sex, bmi, diabetes_onset_date, hba1c, fasting_glucose, data_source) VALUES
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000001', '1968-04-12', 'MALE',   27.4, '2012-06-01', 7.1, 6.2, 'SYNTHETIC'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000002', '1975-09-23', 'FEMALE', 31.2, '2018-03-15', 8.4, 7.8, 'SYNTHETIC'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000003', '1952-11-07', 'MALE',   24.8, '2005-01-20', 6.9, 5.9, 'SYNTHETIC'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000004', '1983-07-30', 'FEMALE', 35.6, '2020-09-10', 9.2, 8.5, 'SYNTHETIC'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000005', '1960-02-18', 'OTHER',  29.1, '2010-12-05', 7.8, 7.1, 'SYNTHETIC')
ON CONFLICT DO NOTHING;

INSERT INTO digital_twin_states (patient_id, twin_version, status, glucose_reading, heart_rate, hrv, sleep_duration, step_count, activity_level, wearable_event_at, cgm_history, last_updated_at, created_at, version) VALUES
    ('a1000000-0000-0000-0000-000000000001', 1, 'INITIALISED', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '[]', now(), now(), 0),
    ('a1000000-0000-0000-0000-000000000002', 1, 'INITIALISED', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '[]', now(), now(), 0),
    ('a1000000-0000-0000-0000-000000000003', 1, 'INITIALISED', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '[]', now(), now(), 0),
    ('a1000000-0000-0000-0000-000000000004', 1, 'INITIALISED', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '[]', now(), now(), 0),
    ('a1000000-0000-0000-0000-000000000005', 1, 'INITIALISED', NULL, NULL, NULL, NULL, NULL, NULL, NULL, '[]', now(), now(), 0)
ON CONFLICT (patient_id) DO NOTHING;
