package com.glucotwin.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.glucotwin.infrastructure.messaging.WearableEventConsumer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

    private final WearableEventConsumer wearableEventConsumer;

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Stream listener container for wearable events.
     * Note: In a production setup this would use consumer groups with retry.
     * For the prototype, uses a simple listener on the stream.
     */
    @Bean
    public StreamMessageListenerContainer<String, org.springframework.data.redis.connection.stream.MapRecord<String, String, String>> wearableStreamContainer(
            RedisConnectionFactory connectionFactory) {

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .<String, org.springframework.data.redis.connection.stream.MapRecord<String, String, String>>builder()
                .pollTimeout(Duration.ofMillis(100))
                .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);

        try {
            container.receive(
                    StreamOffset.create(com.glucotwin.infrastructure.messaging.RedisWearableEventPublisher.STREAM_KEY, ReadOffset.latest()),
                    wearableEventConsumer);
            container.start();
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(RedisStreamConfig.class)
                    .warn("Could not start Redis stream listener container: {}", e.getMessage());
        }

        return container;
    }
}
