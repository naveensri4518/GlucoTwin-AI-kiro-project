package com.glucotwin.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/v1/patients/{patientId}/clinical-insights.
 * The question is optional context — it does not alter data retrieval or calculations.
 */
public record ClinicalInsightRequestDto(
        @Size(max = 500, message = "question must not exceed 500 characters")
        String question) {

    public ClinicalInsightRequestDto {
        // Normalise null to empty string
        question = (question == null) ? "" : question.trim();
    }
}
