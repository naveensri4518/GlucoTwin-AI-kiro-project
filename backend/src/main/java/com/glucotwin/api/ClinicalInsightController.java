package com.glucotwin.api;

import com.glucotwin.api.dto.ClinicalInsightRequestDto;
import com.glucotwin.api.dto.ClinicalInsightResponseDto;
import com.glucotwin.application.insight.GenerateClinicalInsightUseCase;
import com.glucotwin.domain.insight.ClinicalInsightResponse;
import com.glucotwin.domain.shared.PatientId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST endpoint for AI agent-orchestrated clinical decision-support insights.
 *
 * POST /api/v1/patients/{patientId}/clinical-insights
 *
 * <p>This endpoint coordinates the Agent Supervisor, which in turn runs:
 * TwinAnalysisAgent → PredictionAnalysisAgent → RiskEvidenceAgent
 *
 * <p>The response is strictly READ-ONLY — the Digital Twin is never mutated.
 * Output is labelled OBSERVED+PREDICTED. SIMULATED data is never included.
 * The safety disclaimer is always present in the response.
 */
@RestController
@RequestMapping("/api/v1/patients/{patientId}/clinical-insights")
@RequiredArgsConstructor
@Tag(name = "Clinical Insights")
public class ClinicalInsightController {

    private final GenerateClinicalInsightUseCase generateInsightUseCase;

    @PostMapping
    @PreAuthorize("hasAnyRole('CLINICIAN','ADMIN')")
    @Operation(
            summary = "Generate a structured clinical decision-support insight",
            description = """
                    Runs the AI agent orchestration pipeline (TwinAnalysisAgent →
                    PredictionAnalysisAgent → RiskEvidenceAgent) to produce a structured
                    clinical insight for the patient.

                    The Digital Twin is never modified. Output is OBSERVED+PREDICTED only.
                    SIMULATED data is never included. A safety disclaimer is always present.

                    Requires a completed prediction to exist. Call POST /predictions first
                    if no prediction has been generated.

                    For clinical decision support only — not a diagnosis or medical recommendation.
                    """)
    public ResponseEntity<ClinicalInsightResponseDto> generate(
            @PathVariable UUID patientId,
            @Valid @RequestBody ClinicalInsightRequestDto request) {

        ClinicalInsightResponse insight = generateInsightUseCase.execute(
                PatientId.of(patientId),
                request.question());

        return ResponseEntity.ok(ClinicalInsightResponseDto.from(insight));
    }
}
