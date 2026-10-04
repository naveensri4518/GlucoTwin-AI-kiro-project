# Multi-stage Dockerfile for GlucoTwin ML service
FROM python:3.11-slim AS builder
WORKDIR /build
COPY requirements.txt requirements-dev.txt ./
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

FROM python:3.11-slim AS runtime
# Non-root user
RUN useradd -m -s /bin/bash glucotwin
WORKDIR /app
COPY --from=builder /usr/local/lib/python3.11/site-packages /usr/local/lib/python3.11/site-packages
COPY --from=builder /usr/local/bin /usr/local/bin
COPY app ./app
COPY main.py ./
# Copy model artefacts if present (otherwise MODEL_SOURCE=s3 downloads at startup)
COPY models ./models
RUN chown -R glucotwin:glucotwin /app
USER glucotwin
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')" || exit 1
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
