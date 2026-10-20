package com.glucotwin.infrastructure.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration that builds a ChatClient bean from ChatClient.Builder
 * only if Spring AI has initialized a ChatClient.Builder (e.g. when OpenAI is enabled).
 */
@Configuration
public class AiExplanationConfig {

    @Bean
    @ConditionalOnMissingBean(ChatClient.class)
    @ConditionalOnBean(ChatClient.Builder.class)
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
