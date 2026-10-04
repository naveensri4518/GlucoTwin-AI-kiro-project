---
inclusion: always
---

# GlucoTwin AI — Testing Steering

## Governing Principle

Tests are first-class artifacts. They are written alongside code, not after. A feature is not complete until it has passing tests at the appropriate levels. For a clinical decision-support system, untested prediction logic, data validation, and safety constraints are defects — not tech debt.

---

## Test Pyramid

```
          ▲
         /E2E\          (minimal — smoke tests only)
        /──────\
       / Integr.\       (key flows: API → DB, ML predictions)
      /──────────\
     /   Unit     \     (majority — pure logic, fast, isolated)
    ──────────────────
```

Aim for roughly: **70% unit / 25% integration / 5% E2E**.

---

## 1. Unit Testing Standards

### Java (JUnit 5 + Mockito)
- Test one unit (class/method) per test class. Mock all collaborators.
- Use `@ExtendWith(MockitoExtension.class)` — not Spring context unless necessary.
- Arrange-Act-Assert structure with blank line separating each section.
- Name pattern: `methodName_givenCondition_expectedOutcome` (e.g., `predictSpike_givenHighCgmTrend_returnsProbabilityAbove80`).
- Every domain validation rule must have at least: a valid case, an invalid case, and an edge/boundary case.

### Python (pytest)
- One test file per module: `test_{module_name}.py`.
- Use fixtures for shared setup (`@pytest.fixture`).
- Name pattern: `test_{function}_{condition}_{expectation}` (e.g., `test_extract_features_missing_cgm_raises_value_error`).
- All prediction functions must have unit tests for: normal input, minimum valid input, maximum valid input, and missing/null fields.

### TypeScript (Vitest + React Testing Library)
- Test behaviour, not implementation. Query elements by role and label, not by CSS class or test ID.
- Mock API calls with `msw` (Mock Service Worker).
- Name pattern: `it('renders spike probability badge when prediction is available')`.
- `data-testid` attributes are acceptable only when ARIA roles are genuinely insufficient.

---

## 2. Integration Testing

### Backend (Testcontainers)
- Spin up real PostgreSQL and Redis containers for integration tests.
- Test the full slice: HTTP request → controller → service → repository → DB.
- Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` or `MockMvc` for controller tests.
- Flyway migrations run automatically in the test container — tests always run against the current schema.
- Test data is loaded from SQL seed files in `src/test/resources/test-data/`.

### ML Service (pytest + httpx)
- Use `pytest-asyncio` for async FastAPI tests.
- Spin up a test database (SQLite or Testcontainers PostgreSQL) for RAG and persistence tests.
- Test the prediction endpoint end-to-end with a known synthetic twin state; assert probability is in `[0, 1]`.

### Cross-Service
- At least one integration test must verify the full prediction flow: POST wearable event → twin state update → prediction triggered → result returned.
- Use `docker compose` with a `--profile test` to run this flow in CI.

---

## 3. API Testing

- Every REST endpoint defined in the OpenAPI spec must have at least one integration test covering the happy path and one covering the primary error case.
- Use `MockMvc` (Java) or `httpx` test client (Python) — not real HTTP clients against live services in unit/integration tests.
- Validate response shapes against the OpenAPI schema using `assertj-swagger` or `schemathesis`.
- Test HTTP status codes explicitly — do not just assert the response body.

| Status | Must be tested |
|---|---|
| 200 / 201 | Happy path |
| 400 | Invalid input |
| 401 | Missing/invalid JWT |
| 403 | Insufficient role |
| 404 | Unknown resource |
| 422 | Validation failure (FastAPI) |
| 500 | Downstream failure (mocked) |

---

## 4. ML Validation

- Model performance is validated on a held-out synthetic test set before any model version is accepted.
- Minimum acceptance thresholds (enforced in CI via a `validate_model.py` script):
  - AUC-ROC ≥ 0.80
  - Precision ≥ 0.70 at threshold 0.5
  - Recall ≥ 0.65 at threshold 0.5
  - Calibration error (ECE) ≤ 0.10
- Threshold values are defined in a config file, not hardcoded in the validation script.
- A model that fails any threshold must not be deployed. The CI pipeline must fail explicitly with the metric values.
- Feature importance (SHAP) output must be non-empty for every prediction in the validation set.

---

## 5. Property-Based Testing

Use property-based tests for logic where the input space is large or the invariants are more important than specific examples.

### Java (jqwik)
```java
@Property
void spikeProbabilityIsAlwaysBetweenZeroAndOne(
    @ForAll @DoubleRange(min = 1.0, max = 35.0) double cgmValue,
    @ForAll @IntRange(min = 0, max = 50000) int stepCount) {
    double prob = predictionService.predict(cgmValue, stepCount);
    assertThat(prob).isBetween(0.0, 1.0);
}
```

### Python (Hypothesis)
```python
@given(
    cgm_value=st.floats(min_value=1.0, max_value=35.0),
    step_count=st.integers(min_value=0, max_value=50000)
)
def test_probability_always_in_unit_interval(cgm_value, step_count):
    result = predict(cgm_value, step_count)
    assert 0.0 <= result.spike_probability <= 1.0
```

**Required property tests:**
- Prediction probability is always in `[0.0, 1.0]`.
- Feature extraction never returns NaN or Infinity for any valid input.
- Twin state merge is idempotent (applying the same event twice produces the same state as once).
- Simulation result is always labelled `SIMULATED`.
- No EHR field mutation affects a simulation's result after the simulation snapshot is taken.

---

## 6. Data Validation Invariants

These must be tested as explicit test cases (not just assumed):

| Invariant | Test type |
|---|---|
| CGM value is in physiological range [1.0, 35.0] mmol/L | Unit + property |
| Heart rate is in [20, 300] bpm | Unit + property |
| `patientId` is a non-empty UUID | Unit |
| Prediction `spikeProbability` is in [0.0, 1.0] | Unit + property |
| `dataLabel` is exactly one of `OBSERVED`, `PREDICTED`, `SIMULATED` | Unit |
| `predictedAt` timestamp is never in the future | Unit |
| Simulation result is never written to live twin state | Integration |
| Audit log entry is created for every prediction | Integration |

---

## 7. Prediction Constraints (Safety Tests)

These tests enforce the safety boundaries defined in `product.md`. CI must fail if any of these tests fail.

- LLM-generated explanation text must not contain diagnostic keywords: `diagnose`, `you have`, `confirms`, `prescription`, `prescribe`, `take X mg`.
- UI disclaimer text is present on every rendered page (React Testing Library snapshot test).
- API response for predictions never contains a field named `diagnosis` or `prescription`.
- Simulation results are persisted to `simulations` table, never to `predictions` or `twin_states`.

---

## 8. Test Naming and Organisation

### Naming
- Be descriptive and behavioural. A test name should read like a sentence: *"given a high CGM trend, spike probability exceeds 0.7"*.
- Avoid names like `test1`, `testHappyPath`, `testEdgeCase` — they carry no information.

### Organisation
- Mirror the source tree in the test tree (see `structure.md`).
- Group tests by the unit/feature they cover, not by test type.
- Mark slow tests (Testcontainers, full integration) with a tag/annotation (`@Tag("slow")` in JUnit, `@pytest.mark.slow`) so they can be excluded from fast local runs.
- CI always runs all tests. Local `./gradlew test` and `pytest -m "not slow"` are the fast feedback loops.

### Coverage
- Coverage is a signal, not a target. Do not write tests purely to hit a number.
- The following modules require ≥ 80% line coverage enforced in CI:
  - `domain/` (Java)
  - `app/models/` (Python)
  - `app/features/` (Python)
- Exclude: generated code, configuration classes, `notebooks/`.
