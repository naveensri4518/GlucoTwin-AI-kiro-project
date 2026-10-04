# GlucoTwin AI Load Tests

## Prerequisites

```
pip install locust
```

The full stack must be running:
```
docker compose up
```

## Running the load tests

### Wearable stream load test (REQ-029, REQ-031)
100 patients sending 1 wearable event per minute:

```
locust -f locustfile.py \
  --host http://localhost:8080 \
  --users 100 \
  --spawn-rate 10 \
  --run-time 5m \
  --headless \
  --html load_test_report.html
```

Results are written to `load_test_results.json`.

## Acceptance criteria (from REQ-029 / REQ-031)

| Metric | Target |
|---|---|
| p95 end-to-end latency | ≤ 3,000 ms |
| p99 latency | ≤ 8,000 ms |
| No prediction records lost | verified by count assertion |
| No duplicate predictions | verified by unique ID check |

## CI Integration

In GitHub Actions, run:
```yaml
- name: Run load tests
  run: |
    locust -f scripts/load-test/locustfile.py \
      --host ${{ env.API_URL }} \
      --users 50 --spawn-rate 5 --run-time 2m --headless
- name: Upload load test results
  uses: actions/upload-artifact@v4
  with:
    name: load-test-results
    path: load_test_results.json
```
