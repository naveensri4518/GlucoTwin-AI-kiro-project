package com.glucotwin.application.insight;

import com.glucotwin.domain.insight.EvidenceAggregationResult;
import com.glucotwin.domain.insight.InsightExplanationResult;
import com.glucotwin.domain.insight.ObservedSignal;
import com.glucotwin.domain.insight.PredictionAnalysisResult;
import com.glucotwin.domain.insight.TwinAnalysisResult;
import com.glucotwin.domain.prediction.ConfidenceInterval;
import com.glucotwin.domain.prediction.ContributingFactor;
import com.glucotwin.domain.prediction.RiskCategory;
import com.glucotwin.domain.prediction.RiskDirection;
import com.glucotwin.domain.twin.TwinStatus;
import com.glucotwin.infrastructure.config.GlucoTwinProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 13 — InsightExplanationAgent unit tests.
 *
 * The LLM (ChatClient) is ALWAYS mocked — no real API calls are made.
 * Tests verify the agent's safety boundaries, fallback behaviour, and integration contract.
 */
@ExtendWith(MockitoExtension.class)
class InsightExplanationAgentTest {

    // Mock the Spring AI ChatClient fluent interface chain
    @Mock private ChatClient chatClient;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec callSpec;
    @Mock private ChatResponse chatResponse;
    @Mock private Generation generation;
    @Mock private ChatResponseMetadata metadata;
    @Mock private Usage usage;

    private InsightExplanationAgent agent;
    private GlucoTwinProperties properties;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static TwinAnalysisResult twinResult() {
        return new TwinAnalysisResult(3, TwinStatus.ACTIVE,
                List.of(ObservedSignal.of("currentGlucose", "8.4", "mmol/L")),
                List.of(), true, true);
    }

    private static PredictionAnalysisResult predResult() {
        return new PredictionAnalysisResult(
                UUID.randomUUID(), 0.72, RiskCategory.HIGH,
                new ConfidenceInterval(0.61, 0.83),
                List.of(new ContributingFactor("cgm_current", 0.40, RiskDirection.INCREASES_RISK)),
                2, "xgb-v2.0", 3, List.of());
    }

    private static EvidenceAggregationResult evidenceResult() {
        return new EvidenceAggregationResult(
                RiskCategory.HIGH,
                List.of(new ContributingFactor("cgm_current", 0.40, RiskDirection.INCREASES_RISK)),
                "[PREDICTED] HIGH risk. [OBSERVED] currentGlucose=8.4 mmol/L.",
                "No significant uncertainty.", List.of());
    }

    @BeforeEach
    void setUp() {
        properties = new GlucoTwinProperties();
        properties.getAi().getExplanation().setEnabled(true);
        properties.getAi().getExplanation().setTimeoutSeconds(5);
        agent = new InsightExplanationAgent(chatClient, properties);
    }

    // ── Helper: stub successful LLM response ─────────────────────────────────

    private void stubLlm(String text) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(new AssistantMessage(text));
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(50);
        when(usage.getCompletionTokens()).thenReturn(80);
        when(metadata.getModel()).thenReturn("gpt-4o-mini");
    }

    // ── Test 1: Returns explanation when LLM succeeds ─────────────────────────

    @Test
    void explain_llmSucceeds_returnsExplanation() {
        stubLlm("The patient shows elevated glucose risk due to high current CGM reading.");

        InsightExplanationResult result = agent.explain(
                twinResult(), predResult(), evidenceResult(), "Why is risk elevated?");

        assertThat(result.hasContent()).isTrue();
        assertThat(result.explanation())
                .contains("glucose");
        assertThat(result.modelId()).isEqualTo("gpt-4o-mini");
        assertThat(result.promptTokens()).isEqualTo(50);
        assertThat(result.completionTokens()).isEqualTo(80);
    }

    // ── Test 2: Returns unavailable when disabled ──────────────────────────────

    @Test
    void explain_disabled_returnsUnavailableWithoutCallingLlm() {
        properties.getAi().getExplanation().setEnabled(false);

        InsightExplanationResult result = agent.explain(
                twinResult(), predResult(), evidenceResult(), null);

        assertThat(result.hasContent()).isFalse();
        verifyNoInteractions(chatClient);
    }

    // ── Test 3: Non-fatal on LLM exception ────────────────────────────────────

    @Test
    void explain_llmThrows_returnsUnavailableNonFatal() {
        when(chatClient.prompt()).thenThrow(new RuntimeException("API connection refused"));

        assertThatCode(() -> agent.explain(
                twinResult(), predResult(), evidenceResult(), null))
                .doesNotThrowAnyException();

        InsightExplanationResult result = agent.explain(
                twinResult(), predResult(), evidenceResult(), null);
        assertThat(result.hasContent()).isFalse();
    }

    // ── Test 4: Non-fatal on timeout ───────────────────────────────────────────

    @Test
    void explain_llmTimeout_returnsUnavailableNonFatal() {
        properties.getAi().getExplanation().setTimeoutSeconds(0); // instant timeout
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenAnswer(inv -> {
            Thread.sleep(100); // outlast the 0-second timeout
            return callSpec;
        });

        InsightExplanationResult result = agent.explain(
                twinResult(), predResult(), evidenceResult(), null);

        assertThat(result.hasContent()).isFalse();
    }

    // ── Test 5: Empty LLM response returns unavailable ────────────────────────

    @Test
    void explain_llmReturnsBlankText_returnsUnavailable() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        org.springframework.ai.chat.messages.AssistantMessage blankMsg =
                mock(org.springframework.ai.chat.messages.AssistantMessage.class);
        when(blankMsg.getText()).thenReturn("   ");
        when(generation.getOutput()).thenReturn(blankMsg);
        // These stubs are never reached when text is blank — use lenient to avoid strict-stub failure
        lenient().when(chatResponse.getMetadata()).thenReturn(metadata);
        lenient().when(metadata.getUsage()).thenReturn(usage);
        lenient().when(usage.getPromptTokens()).thenReturn(0);
        lenient().when(usage.getCompletionTokens()).thenReturn(0);
        lenient().when(metadata.getModel()).thenReturn("gpt-4o-mini");

        InsightExplanationResult result = agent.explain(
                twinResult(), predResult(), evidenceResult(), null);

        assertThat(result.hasContent()).isFalse();
    }

    // ── Test 6: Question is included in prompt when provided ──────────────────

    @Test
    void explain_questionProvided_llmIsCalled() {
        stubLlm("The elevated reading explains the HIGH risk.");

        InsightExplanationResult result = agent.explain(
                twinResult(), predResult(), evidenceResult(),
                "Why is this patient at HIGH risk?");

        assertThat(result.hasContent()).isTrue();
        verify(requestSpec).user(argThat((String prompt) ->
                prompt.contains("Clinician question")));
    }

    // ── Test 7: Null question is handled safely ────────────────────────────────

    @Test
    void explain_nullQuestion_doesNotThrow() {
        stubLlm("The model predicts HIGH risk based on the available evidence.");

        assertThatCode(() ->
                agent.explain(twinResult(), predResult(), evidenceResult(), null))
                .doesNotThrowAnyException();
    }

    // ── Test 8: No patient PII in user prompt ─────────────────────────────────

    @Test
    void explain_userPrompt_doesNotContainPatientUuid() {
        stubLlm("Some explanation text.");

        agent.explain(twinResult(), predResult(), evidenceResult(), null);

        verify(requestSpec).user(argThat((String prompt) ->
                !prompt.matches(".*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*")));
    }

    // ── Test 9: InsightExplanationResult.unavailable() has no content ─────────

    @Test
    void unavailableResult_hasNoContent() {
        InsightExplanationResult result = InsightExplanationResult.unavailable();
        assertThat(result.hasContent()).isFalse();
        assertThat(result.explanation()).isBlank();
        assertThat(result.modelId()).isEqualTo("none");
    }

    // ── Test 10: Explanation text is stripped of whitespace ───────────────────

    @Test
    void explain_llmResponseStripped() {
        stubLlm("  The risk is elevated.  \n");

        InsightExplanationResult result = agent.explain(
                twinResult(), predResult(), evidenceResult(), null);

        assertThat(result.explanation()).isEqualTo("The risk is elevated.");
    }

    // ── Phase 14: System Prompt Safety Regression Guards ──────────────────────
    // These tests protect against accidental weakening of the Phase 13 safety prompt.
    // They inspect the SYSTEM_PROMPT constant — no real LLM calls.

    @Test
    void systemPrompt_containsExplicitNoDiagnoseRule() throws Exception {
        var field = InsightExplanationAgent.class.getDeclaredField("SYSTEM_PROMPT");
        field.setAccessible(true);
        String prompt = (String) field.get(null);

        assertThat(prompt)
                .as("System prompt must contain an explicit 'Do NOT diagnose' rule")
                .containsIgnoringCase("Do NOT diagnose");
    }

    @Test
    void systemPrompt_containsExplicitNoPrescribeRule() throws Exception {
        var field = InsightExplanationAgent.class.getDeclaredField("SYSTEM_PROMPT");
        field.setAccessible(true);
        String prompt = (String) field.get(null);

        assertThat(prompt)
                .as("System prompt must contain an explicit 'Do NOT prescribe' rule")
                .containsIgnoringCase("Do NOT prescribe");
    }

    @Test
    void systemPrompt_containsExplicitNoFabricateNumericValuesRule() throws Exception {
        var field = InsightExplanationAgent.class.getDeclaredField("SYSTEM_PROMPT");
        field.setAccessible(true);
        String prompt = (String) field.get(null);

        assertThat(prompt)
                .as("System prompt must contain explicit prohibition on inventing/fabricating numeric values")
                .containsIgnoringCase("fabricate");
        // Also check for the related terms to ensure the rule is substantive
        assertThat(prompt).containsIgnoringCase("numeric");
    }
}
