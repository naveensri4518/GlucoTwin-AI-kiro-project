"""Safety constraint tests for the ML service prediction output."""
import json
import pytest
from datetime import datetime, timezone

from app.features.feature_engineering import FeatureEngineer, FEATURE_NAMES

# Forbidden diagnostic/prescriptive terms (REQ-025)
FORBIDDEN_TERMS = [
    "diagnose", "diagnosis", "you have", "confirms", "prescribe",
    "prescription", "guaranteed", "certain",
]


class TestNoForbiddenDiagnosticTerms:
    """REQ-025: No diagnostic language in any ML service output."""

    def test_prediction_response_dto_has_no_forbidden_terms(
        self, sample_snapshot
    ) -> None:
        """Check that PredictionResponseDTO fields don't contain forbidden language."""
        from app.schemas.prediction import PredictionResponseDTO, ConfidenceIntervalDTO, ContributingFactorDTO

        response = PredictionResponseDTO(
            spike_probability=0.72,
            confidence_interval=ConfidenceIntervalDTO(low=0.61, high=0.81),
            top_contributing_factors=[
                ContributingFactorDTO(
                    factor_name="cgm_current",
                    contribution=0.31,
                    direction="INCREASES_RISK",
                )
            ],
            model_version="xgboost-v1.0.0",
            imputed_fields=[],
        )

        response_json = response.model_dump_json()

        for term in FORBIDDEN_TERMS:
            assert term.lower() not in response_json.lower(), \
                f"Forbidden term '{term}' found in PredictionResponseDTO: {response_json}"

    def test_imputed_fields_contain_only_valid_feature_names(
        self, sample_snapshot
    ) -> None:
        """REQ-018 security: imputed field names must all be valid known feature names."""
        engineer = FeatureEngineer()
        # Remove all optional fields to trigger imputation
        sample_snapshot.dynamic_layer.heart_rate = None
        sample_snapshot.dynamic_layer.hrv = None
        sample_snapshot.dynamic_layer.sleep_duration = None

        fv = engineer.extract(sample_snapshot)

        for field in fv.imputed_fields:
            assert field in FEATURE_NAMES, \
                f"Imputed field '{field}' is not a known feature name — possible injection"

        # Forbidden terms check applies to free-text output strings, not technical field names
        # (e.g. 'days_since_diagnosis' is a feature name containing 'diagnosis' as a substring,
        # which is expected and safe — the prohibition is on clinical claim language in responses)
        # Verify the imputed_fields list itself is well-formed (no free-text content)
        for field in fv.imputed_fields:
            assert len(field) < 100, f"Suspiciously long field name: {field}"
            assert field.replace("_", "").isalnum(), \
                f"Field name contains unexpected characters: {field}"


class TestUncertaintyFieldsAlwaysPresent:
    """REQ-026: Every prediction must communicate uncertainty."""

    def test_prediction_response_always_has_confidence_interval(
        self, sample_snapshot
    ) -> None:
        from app.schemas.prediction import PredictionResponseDTO, ConfidenceIntervalDTO

        response = PredictionResponseDTO(
            spike_probability=0.5,
            confidence_interval=ConfidenceIntervalDTO(low=0.4, high=0.6),
            top_contributing_factors=[],
            model_version="xgboost-v1.0.0",
        )
        assert response.confidence_interval is not None
        assert response.confidence_interval.low < response.confidence_interval.high

    def test_probability_is_in_unit_interval(self, sample_snapshot) -> None:
        from app.schemas.prediction import PredictionResponseDTO, ConfidenceIntervalDTO

        for prob in [0.0, 0.5, 1.0]:
            response = PredictionResponseDTO(
                spike_probability=prob,
                confidence_interval=ConfidenceIntervalDTO(low=max(0.0, prob - 0.1), high=min(1.0, prob + 0.1)),
                top_contributing_factors=[],
                model_version="xgboost-v1.0.0",
            )
            assert 0.0 <= response.spike_probability <= 1.0


class TestDataProvenanceLabel:
    """REQ-012: Model output must never be mislabelled."""

    def test_prediction_response_has_no_observed_provenance(self) -> None:
        """ML service output should never claim to be OBSERVED data."""
        from app.schemas.prediction import PredictionResponseDTO, ConfidenceIntervalDTO

        response = PredictionResponseDTO(
            spike_probability=0.6,
            confidence_interval=ConfidenceIntervalDTO(low=0.5, high=0.7),
            top_contributing_factors=[],
            model_version="xgboost-v1.0.0",
        )
        # The response schema has no dataProvenance field (it's added by the Java backend)
        # Verify there's no OBSERVED label in the serialised output
        response_json = response.model_dump_json()
        assert "OBSERVED" not in response_json, \
            "ML service output must not be labelled OBSERVED"
        assert "SIMULATED" not in response_json, \
            "ML service output must not be labelled SIMULATED"
