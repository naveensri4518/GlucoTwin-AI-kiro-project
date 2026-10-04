-- Synthetic wearable event seed data — 4 events per patient (20 total)
-- ALL records are purely synthetic. data_source = 'SYNTHETIC'.

INSERT INTO wearable_events_raw (event_id, patient_id, glucose_reading, heart_rate, hrv, sleep_duration, sleep_stage, step_count, activity_level, event_timestamp, processing_status, data_source) VALUES
    -- Patient 1
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000001', 7.2, 68, 52, 7.0, 'DEEP',  3200, 'SEDENTARY', now() - interval '3 hours', 'PROCESSED', 'SYNTHETIC'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000001', 8.1, 72, 48, 7.0, 'LIGHT', 4500, 'LIGHT',    now() - interval '2 hours', 'PROCESSED', 'SYNTHETIC'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000001', 8.9, 75, 44, 7.0, 'AWAKE', 5100, 'LIGHT',    now() - interval '1 hour',  'PROCESSED', 'SYNTHETIC'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000001', 9.4, 80, 40, 7.0, 'AWAKE', 6200, 'MODERATE', now() - interval '30 minutes', 'PROCESSED', 'SYNTHETIC'),
    -- Patient 2
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000002', 11.2, 85, 30, 5.5, 'AWAKE', 2100, 'SEDENTARY', now() - interval '3 hours', 'PROCESSED', 'SYNTHETIC'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000002', 12.8, 88, 28, 5.5, 'AWAKE', 2400, 'SEDENTARY', now() - interval '2 hours', 'PROCESSED', 'SYNTHETIC'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000002', 14.1, 90, 25, 5.5, 'AWAKE', 2600, 'LIGHT',    now() - interval '1 hour',  'PROCESSED', 'SYNTHETIC'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000002', 15.3, 92, 22, 5.5, 'AWAKE', 2800, 'LIGHT',    now() - interval '30 minutes', 'PROCESSED', 'SYNTHETIC'),
    -- Patient 3
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000003', 5.8, 62, 65, 8.0, 'REM',   8500, 'VIGOROUS', now() - interval '3 hours', 'PROCESSED', 'SYNTHETIC'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000003', 6.1, 65, 61, 8.0, 'LIGHT', 9200, 'MODERATE', now() - interval '2 hours', 'PROCESSED', 'SYNTHETIC'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000003', 6.4, 67, 58, 8.0, 'LIGHT', 9800, 'MODERATE', now() - interval '1 hour',  'PROCESSED', 'SYNTHETIC'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000003', 6.6, 70, 55, 8.0, 'AWAKE', 10200, 'LIGHT',   now() - interval '30 minutes', 'PROCESSED', 'SYNTHETIC'),
    -- Patient 4
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000004', 16.5, 95, 18, 4.5, 'AWAKE', 1500, 'SEDENTARY', now() - interval '3 hours', 'PROCESSED', 'SYNTHETIC'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000004', 17.2, 98, 16, 4.5, 'AWAKE', 1600, 'SEDENTARY', now() - interval '2 hours', 'PROCESSED', 'SYNTHETIC'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000004', 18.1, 100, 14, 4.5, 'AWAKE', 1700, 'SEDENTARY', now() - interval '1 hour', 'PROCESSED', 'SYNTHETIC'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000004', 19.0, 102, 12, 4.5, 'AWAKE', 1800, 'SEDENTARY', now() - interval '30 minutes', 'PROCESSED', 'SYNTHETIC'),
    -- Patient 5
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000005', 8.5, 74, 46, 6.5, 'LIGHT', 5500, 'LIGHT',    now() - interval '3 hours', 'PROCESSED', 'SYNTHETIC'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000005', 9.0, 76, 43, 6.5, 'AWAKE', 6100, 'LIGHT',    now() - interval '2 hours', 'PROCESSED', 'SYNTHETIC'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000005', 9.8, 79, 40, 6.5, 'AWAKE', 6800, 'MODERATE', now() - interval '1 hour',  'PROCESSED', 'SYNTHETIC'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000005', 10.5, 82, 36, 6.5, 'AWAKE', 7400, 'MODERATE', now() - interval '30 minutes', 'PROCESSED', 'SYNTHETIC')
ON CONFLICT DO NOTHING;
