"""GlucoTwin ML Service entry point."""
from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from typing import AsyncGenerator

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from app.config import settings
from app.exceptions import (
    GlucoseReadingRequiredError,
    ModelLoadError,
    FeatureExtractionError,
    GlucoTwinMLException,
)
from app.logging_config import configure_logging
from app.middleware.internal_token import InternalTokenMiddleware

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Lifespan: load model on startup
# ---------------------------------------------------------------------------
_model = None


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:  # type: ignore[type-arg]
    """Load model artefact at startup; release on shutdown."""
    global _model
    configure_logging(settings.log_level)
    logger.info("GlucoTwin ML Service starting", extra={"model_version": settings.model_version})

    try:
        from app.models.model_loader import load_model
        from pathlib import Path
        _model = load_model(Path(settings.model_dir))
        app.state.model = _model
        logger.info("Model loaded successfully", extra={"model_version": _model.get_model_version()})
    except ModelLoadError as e:
        logger.warning("Model not loaded at startup (will serve 503 on predict): %s", e)
        app.state.model = None

    yield

    logger.info("GlucoTwin ML Service shutting down")


# ---------------------------------------------------------------------------
# App
# ---------------------------------------------------------------------------
app = FastAPI(
    title="GlucoTwin ML Service",
    description=(
        "Internal ML service for glucose spike prediction. "
        "For research and decision support only. Not a diagnostic system."
    ),
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
)

# Middleware: internal token auth (exempt: /health)
app.add_middleware(InternalTokenMiddleware)

# ---------------------------------------------------------------------------
# Exception handlers
# ---------------------------------------------------------------------------

@app.exception_handler(GlucoseReadingRequiredError)
async def glucose_required_handler(request: Request, exc: GlucoseReadingRequiredError) -> JSONResponse:
    return JSONResponse(
        status_code=422,
        content={"error": "GLUCOSE_READING_REQUIRED", "message": str(exc)},
    )


@app.exception_handler(ModelLoadError)
async def model_load_handler(request: Request, exc: ModelLoadError) -> JSONResponse:
    return JSONResponse(
        status_code=503,
        content={"error": "MODEL_NOT_READY", "message": "ML model is not available"},
    )


@app.exception_handler(FeatureExtractionError)
async def feature_extraction_handler(request: Request, exc: FeatureExtractionError) -> JSONResponse:
    return JSONResponse(
        status_code=422,
        content={"error": "FEATURE_EXTRACTION_ERROR", "message": str(exc)},
    )


@app.exception_handler(ValidationError)
async def pydantic_validation_handler(request: Request, exc: ValidationError) -> JSONResponse:
    return JSONResponse(
        status_code=422,
        content={"error": "VALIDATION_ERROR", "message": exc.errors()},
    )


@app.exception_handler(Exception)
async def generic_handler(request: Request, exc: Exception) -> JSONResponse:
    logger.error("Unhandled exception: %s", exc, exc_info=True)
    return JSONResponse(
        status_code=500,
        content={"error": "INTERNAL_ERROR", "message": "An internal error occurred"},
    )


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@app.get("/health", tags=["Health"])
async def health() -> dict:  # type: ignore[type-arg]
    """Health check — exempt from internal token auth."""
    model = getattr(app.state, "model", None)
    return {
        "status": "ok",
        "modelVersion": model.get_model_version() if model else "not_loaded",
    }


@app.get("/model/info", tags=["Model"])
async def model_info(request: Request) -> dict:  # type: ignore[type-arg]
    """Returns model metadata."""
    model = getattr(request.app.state, "model", None)
    if model is None:
        return JSONResponse(
            status_code=503,
            content={"error": "MODEL_NOT_READY", "message": "Model not loaded"},
        )
    return {
        "version": model.get_model_version(),
        "description": "XGBoost glucose spike predictor v1",
    }


# Include prediction router
from app.api.prediction_router import router as prediction_router  # noqa: E402
app.include_router(prediction_router, prefix="", tags=["Prediction"])
