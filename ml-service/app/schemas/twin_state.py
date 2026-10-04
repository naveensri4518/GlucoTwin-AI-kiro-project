"""Pydantic schemas for Digital Twin state transfer."""
from __future__ import annotations
from typing import Optional, List
from datetime import datetime, date
from pydantic import BaseModel, Field


class CgmReadingDTO(BaseModel):
    value: float
    timestamp: datetime


class MedicationDTO(BaseModel):
    name: str
    dose: str
    frequency: str


class LabResultDTO(BaseModel):
    type: str
    value: float
    unit: str
    recorded_at: datetime


class StaticLayerDTO(BaseModel):
    date_of_birth: Optional[date] = None
    sex: Optional[str] = None
    bmi: Optional[float] = None
    diabetes_onset_date: Optional[date] = None
    hba1c: Optional[float] = None
    fasting_glucose: Optional[float] = None
    medications: List[MedicationDTO] = Field(default_factory=list)
    lab_results: List[LabResultDTO] = Field(default_factory=list)


class DynamicLayerDTO(BaseModel):
    glucose_reading: Optional[float] = None
    heart_rate: Optional[float] = None
    hrv: Optional[float] = None
    sleep_duration: Optional[float] = None
    sleep_stage: Optional[str] = None
    step_count: Optional[int] = None
    activity_level: Optional[str] = None
    event_timestamp: Optional[datetime] = None
    data_quality_warnings: List[str] = Field(default_factory=list)
    cgm_history: List[CgmReadingDTO] = Field(default_factory=list)


class TwinStateSnapshotDTO(BaseModel):
    patient_id: str
    twin_version: int
    status: str
    static_layer: Optional[StaticLayerDTO] = None
    dynamic_layer: Optional[DynamicLayerDTO] = None
    snapshot_taken_at: datetime
