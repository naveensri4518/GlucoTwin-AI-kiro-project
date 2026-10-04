"""Custom exception hierarchy for the ML service."""


class GlucoTwinMLException(Exception):
    """Base exception for ML service."""


class GlucoseReadingRequiredError(GlucoTwinMLException):
    """Raised when glucose reading is absent but required for prediction."""


class ModelLoadError(GlucoTwinMLException):
    """Raised when the ML model cannot be loaded."""


class FeatureExtractionError(GlucoTwinMLException):
    """Raised when feature extraction fails."""


class ValidationError(GlucoTwinMLException):
    """Raised for invalid input data."""
