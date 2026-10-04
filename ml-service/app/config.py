"""Application configuration loaded from environment variables."""
from pydantic_settings import BaseSettings
from pydantic import Field


class Settings(BaseSettings):
    # ML model
    model_dir: str = Field(default="models/xgboost-v1.0.0", alias="MODEL_DIR")
    model_source: str = Field(default="local", alias="MODEL_SOURCE")
    model_version: str = Field(default="xgboost-v1.0.0", alias="MODEL_VERSION")

    # Service security
    ml_service_internal_token: str = Field(default="dev-internal-token", alias="ML_SERVICE_INTERNAL_TOKEN")

    # Database (for future RAG / pgvector)
    postgres_url: str = Field(default="postgresql://glucotwin:glucotwin@localhost:5432/glucotwin", alias="POSTGRES_URL")

    # Logging
    log_level: str = Field(default="INFO", alias="LOG_LEVEL")
    log_format: str = Field(default="json", alias="LOG_FORMAT")

    # Glucose spike threshold (used for training label generation)
    glucose_spike_threshold_mmol: float = Field(default=10.0, alias="GLUCOSE_SPIKE_THRESHOLD")

    class Config:
        env_file = ".env"
        populate_by_name = True


settings = Settings()
