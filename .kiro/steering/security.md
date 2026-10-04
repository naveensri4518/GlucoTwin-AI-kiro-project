---
inclusion: always
---

# GlucoTwin AI — Security Steering

## Governing Principle

This system processes simulated healthcare data about real medical conditions. Even with synthetic data, security practices must match what a production clinical system would require. Security is not a sprint-end concern — it is a design constraint from the first line of code.

---

## 1. No Hardcoded Secrets

- **Zero tolerance.** No passwords, API keys, connection strings, tokens, or certificates may appear in any source file, Dockerfile, or committed configuration file.
- Use environment variables for all secrets in every environment.
- Pre-commit hooks must run `gitleaks` or equivalent secret-scanning before any commit reaches the remote.
- If a secret is accidentally committed, rotate it immediately — do not simply delete the commit.

**Compliant pattern:**
```java
// application.yml — structural default only
spring.datasource.url: ${POSTGRES_URL}
spring.datasource.username: ${POSTGRES_USER}
spring.datasource.password: ${POSTGRES_PASSWORD}
```

**Non-compliant pattern (never do this):**
```java
spring.datasource.password: mypassword123   # FORBIDDEN
```

---

## 2. Environment Variable Policy

| Environment | Secret source | Who manages |
|---|---|---|
| Local dev | `.env` file (gitignored), Docker Compose `env_file` | Developer |
| CI/CD | GitHub Actions encrypted secrets | Repo admin |
| Production | AWS Secrets Manager → ECS task definition injection | Platform team / CDK |

- `.env` files are always in `.gitignore`. Provide `.env.example` with placeholder values and comments.
- No secret is logged, even at DEBUG level. Log sanitisation is mandatory.
- Rotate secrets on a schedule (90-day maximum for API keys).

---

## 3. Input Validation

- Validate **all** incoming data at the service boundary — REST controllers, message consumers, and MCP tool handlers.
- Use Jakarta Bean Validation (`@NotNull`, `@Size`, `@Pattern`, etc.) on all Java DTOs.
- Use Pydantic models with strict field types on all Python FastAPI endpoints.
- Use Zod schemas for all forms and API response parsing in the React frontend.
- Reject requests that fail validation with `400 Bad Request` and a structured error body. Never pass invalid data to inner layers.
- Clinical range validation: glucose readings outside physiologically plausible bounds (e.g., < 1 mmol/L or > 35 mmol/L) must be flagged as `DATA_QUALITY_WARNING`, not silently accepted.

---

## 4. Authentication and Authorisation

- All API endpoints (except health checks) require authentication.
- Use **JWT (RS256)** bearer tokens. Symmetric (HS256) secrets are not permitted.
- Token validation happens in the Spring Security filter chain; never in business logic.
- Authorisation model: role-based (`ROLE_CLINICIAN`, `ROLE_RESEARCHER`, `ROLE_ADMIN`).
- A clinician may only access patients assigned to them (row-level data scoping).
- Admin endpoints are on a separate path prefix (`/api/admin/`) with explicit role checks.
- The ML service and Redis are **not** directly accessible by the frontend — all calls route through the backend.

---

## 5. Healthcare Data Safety

- All patient-related data (even synthetic) must be treated as if it were PHI (Protected Health Information).
- Data fields classified as sensitive: `patientId`, `dateOfBirth`, `diagnosisCodes`, `medications`, `labResults`, `glucoseReadings`.
- Sensitive fields must not appear in:
  - Application logs (use `patientId` hash or opaque reference in logs).
  - Error messages returned to the client.
  - URL path parameters (use body or query params with authentication).
- Database columns containing sensitive data must be encrypted at rest (PostgreSQL Transparent Data Encryption or column-level encryption via pgcrypto).
- All data access is logged to an immutable audit table: `who`, `what`, `when`, `which patient`.

---

## 6. Synthetic and Anonymised Data Policy

- **No real patient data** may be used at any stage: development, testing, CI, demos, or documentation.
- Acceptable data sources:
  - Procedurally generated synthetic data (Faker, Synthea).
  - Publicly available, de-identified datasets (e.g., MIMIC-III with appropriate DUA).
  - Open research datasets explicitly licensed for use.
- Synthetic data must be clearly marked. A `data_source` field or metadata tag of `SYNTHETIC` or `ANONYMISED` is required on all seed and test records.
- Never use production data to validate or tune models, even if "anonymised" manually.

---

## 7. API Security

- **TLS everywhere.** All external traffic uses HTTPS (TLS 1.2 minimum, TLS 1.3 preferred). HTTP is rejected or redirected.
- **Rate limiting.** All public endpoints enforce rate limits at the API Gateway level. Prediction and simulation endpoints have stricter limits (they are compute-intensive).
- **CORS.** Allow only the known frontend origin(s). `Access-Control-Allow-Origin: *` is forbidden in production.
- **CSRF.** Frontend uses JWT in `Authorization` header (not cookies) — CSRF tokens are not required, but SameSite cookie policy applies to any session cookies.
- **Content-Type enforcement.** Reject requests with unexpected `Content-Type` values.
- **Error responses.** Never expose stack traces, internal class names, or SQL to the client. Return generic error messages; log detail server-side.
- **Dependency scanning.** Run `dependabot` or `OWASP Dependency-Check` in CI. Block merges on critical CVEs.

---

## 8. AI Safety and Prompt Injection

- **Prompt injection defence.** User-supplied text that flows into an LLM prompt must be treated as untrusted. Sanitise by:
  - Escaping or quoting user content before inserting into system/user message templates.
  - Keeping system prompts and user content in **separate message roles** (never concatenate them).
  - Rejecting inputs that contain delimiter tokens (e.g., `<|im_start|>`, `###`, `SYSTEM:`).
- **Output filtering.** All LLM-generated text passes through a post-processing filter before being returned:
  - Strip any text containing diagnostic claims ("you have", "this confirms", "diagnosis is").
  - Strip any prescriptive claims ("take X mg", "stop taking", "prescribe").
  - If filtering removes content, return a safe fallback message; never return partial filtered text.
- **LLM output is never trusted as structured data.** Parse LLM JSON responses through Pydantic/Zod schema validation; reject malformed output.
- **No tool calls from unverified agents.** MCP tools verify the caller's identity before execution.
- **Jailbreak resistance.** System prompts must include explicit role boundaries. Log any detected jailbreak attempts.
- **Model version pinning.** Pin the OpenAI model version (e.g., `gpt-4o-2024-08-06`). Do not use floating `gpt-4o` without a version suffix in production — model behaviour changes on provider updates.
