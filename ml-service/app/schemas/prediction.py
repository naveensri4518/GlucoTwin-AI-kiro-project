"""Pydantic schemas for prediction request/response."""
from __future__ import annotations
from typing import List, Optional
from pydantic import BaseModel, Field


class ContributingFactorDTO(BaseModel):
    factor_name: str
    contribution: float
    direction: str  # INCREASES_RISK | DECREASES_RISK


class ConfidenceIntervalDTO(BaseModel):
    low: float
    high: float


class PredictionResponseDTO(BaseModel):
    spike_probability: float = Field(ge=0.0, le=1.0)
    confidence_interval: ConfidenceIntervalDTO
    top_contributing_factors: List[ContributingFactorDTO]
    model_version: str
    imputed_fields: List[str] = Field(default_factory=list)


class ErrorResponseDTO(BaseModel):
    error: str
    message: str
