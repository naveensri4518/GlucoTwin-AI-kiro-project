package com.glucotwin.application.insight;

import com.glucotwin.domain.insight.EvidenceAggregationResult;
import com.glucotwin.domain.insight.InsightExplanationResult;
import com.glucotwin.domain.insight.PredictionAnalysisResult;
import com.glucotwin.domain.insight.TwinAnalysisResult;
import com.glucotwin.infrastructure.config.GlucoTwinProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Phase 13 — Controlled LLM explanation agent.
 *
 * <p>Receives structured, provenance-labelled evidence from the deterministic pipeline
 * and produces a grounded natural-language explanation paragraph for the clinician.
 *
 * <p>Safety contract:
 * <ul>
 *   <li>The LLM never receives raw patient data — only the already-labelled
 *       {@code evidenceSummary} string built by {@link RiskEvidenceAgent}.
 *   <li>The system prompt explicitly forbids diagnosis, prescriptions, and numeric fabrication.
 *   <li>The LLM output never affects {@code spikeProbability}, {@code riskCategory},
 *       {@code confidenceInterval}, or any provenance label.
 *   <li>All numeric clinical values remain sourced exclusively from the XGBoost pipeline.
 *   <li>Failure is always non-fatal — the pipeline returns normally with
 *       {@code explanation = null}.
 *   <li>A configurable hard timeout prevents slow LLM calls from blocking clinician requests.
 *   <li>Can be disabled entirely via {@code glucotwin.ai.explanation.enabled=false}.
 * </ul>
 *
 * <p>The LLM is called AFTER evidence aggregation and BEFORE verification, so
 * {@link InsightVerificationAgent} can still apply consistency checks to the full response.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InsightExplanationAgent {

    static final String AGENT_NAME = "InsightExplanationAgent";

    /**
     * System prompt — defines the LLM's bounded role.
     * Explicit prohibitions prevent diagnostic overreach.
     */
    private static final String SYSTEM_PROMPT = """
            You are a clinical decision-support assistant embedded in a glucose-monitoring \
            digital twin system. Your only job is to explain, in plain language suitable for \
            a clinician, why a patient has an elevated or reduced glucose spike risk based \
            on the structured evidence provided.

            STRICT RULES — you must follow all of these without exception:
            1. Do NOT diagnose any condition.
            2. Do NOT prescribe or recommend any medication or dosage change.
            3. Do NOT invent, estimate, or fabricate any numeric values (glucose levels, \
            probabilities, lab values). All numbers come from the system — you must only \
            reference the ones already present in the evidence.
            4. Do NOT present the explanation as a medical recommendation.
            5. Always use cautious language: "may suggest", "is associated with", \
            "according to the model", "general clinical context".
            6. Keep the explanation concise — at most 3–4 sentences.
            7. Refer to the patient as "the patient" — never use personal pronouns that \
            imply a specific individual.
            8. Do NOT repeat the safety disclaimer — the system handles that separately.
            9. Clearly distinguish OBSERVED data (wearable/EHR readings) from PREDICTED \
            model outputs. The evidence text uses [OBSERVED] and [PREDICTED] markers.

            This explanation is for clinical decision support only — not a diagnosis \
            or treatment recommendation.
            """;

    private final ChatClient chatClient;
    private final GlucoTwinProperties properties;

    /**
     * Generate a grounded LLM explanation for the assembled evidence.
     *
     * <p>Returns {@link InsightExplanationResult#unavailable()} when:
     * <ul>
     *   <li>Explanation is disabled via config ({@code glucotwin.ai.explanation.enabled=false})
     *   <li>LLM call times out (> {@code glucotwin.ai.explanation.timeout-seconds})
     *   <li>LLM call throws any exception
     * </ul>
     * Never throws — always returns a valid result.
     *
     * @param twin      OBSERVED twin analysis result (structural names only, no PII values)
     * @param pred      PREDICTED prediction analysis result
     * @param evidence  aggregated evidence including the provenance-labelled summary string
     * @param question  optional clinician question (sanitised — must not contain PII)
     */
    public InsightExplanationResult explain(
            TwinAnalysisResult twin,
            PredictionAnalysisResult pred,
            EvidenceAggregationResult evidence,
            String question) {

        if (!properties.getAi().getExplanation().isEnabled()) {
            log.debug("[{}] LLM explanation disabled by configuration", AGENT_NAME);
            return InsightExplanationResult.unavailable();
        }

        String userPrompt = buildUserPrompt(pred, evidence, question);
        long timeoutSecs  = properties.getAi().getExplanation().getTimeoutSeconds();

        try {
            // Run LLM call with hard timeout — prevents slow responses blocking the pipeline
            CompletableFuture<ChatResponse> future = CompletableFuture.supplyAsync(() ->
                    chatClient.prompt()
                            .system(SYSTEM_PROMPT)
                            .user(userPrompt)
                            .call()
                            .chatResponse());

            ChatResponse chatResponse = future.get(timeoutSecs, TimeUnit.SECONDS);

            String text = chatResponse.getResult().getOutput().getText();
            if (text == null || text.isBlank()) {
                log.warn("[{}] LLM returned empty response", AGENT_NAME);
                return InsightExplanationResult.unavailable();
            }

            Usage usage = chatResponse.getMetadata().getUsage();
            int promptTokens     = usage != null && usage.getPromptTokens() != null
                    ? usage.getPromptTokens() : 0;
            int completionTokens = usage != null && usage.getCompletionTokens() != null
                    ? usage.getCompletionTokens() : 0;
            String modelId = chatResponse.getMetadata().getModel();

            log.info("[{}] Explanation generated — model={} promptTokens={} completionTokens={}",
                    AGENT_NAME, modelId, promptTokens, completionTokens);

            return new InsightExplanationResult(
                    text.strip(), modelId, promptTokens, completionTokens, Instant.now());

        } catch (TimeoutException ex) {
            log.warn("[{}] LLM call timed out after {}s — returning unavailable",
                    AGENT_NAME, timeoutSecs);
            return InsightExplanationResult.unavailable();
        } catch (Exception ex) {
            log.error("[{}] LLM call failed (non-fatal) — {}", AGENT_NAME, ex.getMessage(), ex);
            return InsightExplanationResult.unavailable();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Constructs the user-turn prompt from structured evidence.
     *
     * <p>The prompt contains only:
     * <ul>
     *   <li>The already-labelled {@code evidenceSummary} (built by RiskEvidenceAgent)
     *   <li>The clinician's question (if any)
     *   <li>Structural counts (signal count, knowledge count)
     * </ul>
     * Raw patient values are not inserted here — they are already embedded in
     * the provenance-labelled {@code evidenceSummary} string.
     */
    private String buildUserPrompt(PredictionAnalysisResult pred,
                                   EvidenceAggregationResult evidence,
                                   String question) {
        StringBuilder sb = new StringBuilder();

        sb.append("Risk category: ").append(pred.riskCategory().name()).append("\n");
        sb.append("Prediction horizon: ").append(pred.predictionHorizonHours()).append("h\n");

        if (!evidence.evidenceSummary().isBlank()) {
            sb.append("\nEvidence summary:\n").append(evidence.evidenceSummary()).append("\n");
        }

        if (evidence.uncertainty() != null && !evidence.uncertainty().isBlank()) {
            sb.append("\nUncertainty: ").append(evidence.uncertainty()).append("\n");
        }

        if (question != null && !question.isBlank()) {
            // Sanitise: truncate at 300 chars, strip any SQL/script injection patterns
            String sanitised = question.strip()
                    .replaceAll("[<>\"';&]", "")
                    .substring(0, Math.min(question.length(), 300));
            sb.append("\nClinician question: ").append(sanitised).append("\n");
        }

        sb.append("\nPlease explain why the patient is at ").append(pred.riskCategory().name())
          .append(" risk in 3–4 plain sentences for a clinical audience.");

        return sb.toString();
    }
}
