package com.glucotwin.infrastructure.config;

import com.glucotwin.application.insight.InsightExplanationAgent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Spring AI auto-configurations for audio, speech, transcription,
 * and chat remain disabled without an OPENAI_API_KEY when AI features are disabled,
 * preventing startup crashes like BeanCreationException: openAiAudioSpeechModel.
 */
class AiConfigurationStartupTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    OpenAiAudioSpeechAutoConfiguration.class,
                    OpenAiAudioTranscriptionAutoConfiguration.class,
                    OpenAiChatAutoConfiguration.class,
                    AiExplanationConfig.class
            ));

    @Test
    void startup_withoutOpenAiApiKey_speechAndTranscriptionDisabled() {
        contextRunner
                .withPropertyValues(
                        "spring.ai.model.chat=none",
                        "spring.ai.model.audio.speech=none",
                        "spring.ai.model.audio.transcription=none",
                        "spring.ai.model.embedding=none",
                        "spring.ai.model.image=none",
                        "spring.ai.model.moderation=none"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("openAiAudioSpeechModel");
                    assertThat(context).doesNotHaveBean("openAiAudioTranscriptionModel");
                    assertThat(context).doesNotHaveBean("openAiChatModel");
                    assertThat(context).doesNotHaveBean("chatClient");
                });
    }

    @Test
    void insightExplanationAgent_initializesSafelyWithoutChatClientBean() {
        GlucoTwinProperties properties = new GlucoTwinProperties();
        properties.getAi().getExplanation().setEnabled(false);

        // When ChatClient is null because AI is disabled/unconfigured, agent should not fail
        InsightExplanationAgent agent = new InsightExplanationAgent(null, properties);

        var result = agent.explain(null, null, null, null);
        assertThat(result.hasContent()).isFalse();
        assertThat(result.explanation()).isBlank();
    }
}
