package com.astro.api.chat.messaging;

import com.astro.api.chat.dto.response.ChatSendResponse;
import com.astro.api.chat.service.ChatDeliveryUnavailableException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatMessagingJsonTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(SimpMessagingTemplate.class, () -> mock(SimpMessagingTemplate.class))
            .withUserConfiguration(RedisChatMessagePublisher.class, ChatRedisMessageListener.class);

    @Test
    void startsChatComponentsWithBootAutoConfiguredMapper() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed()
                    .hasSingleBean(JsonMapper.class)
                    .hasSingleBean(RedisChatMessagePublisher.class)
                    .hasSingleBean(ChatRedisMessageListener.class);
        });
    }

    @Test
    void deliversPublishedJsonWithOriginalFieldsAndInstant() {
        contextRunner.run(context -> {
            StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);
            SimpMessagingTemplate messaging = context.getBean(SimpMessagingTemplate.class);
            when(redis.convertAndSend(eq(RedisChatMessagePublisher.DELIVERY_CHANNEL), anyString()))
                    .thenReturn(1L);
            ChatSendResponse response = new ChatSendResponse("client-1", "message-1", "conversation-1",
                    7L, "Olá!", null, Instant.parse("2026-10-27T12:00:00.123Z"));

            context.getBean(RedisChatMessagePublisher.class).publish(9L, response);

            ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
            verify(redis).convertAndSend(eq(RedisChatMessagePublisher.DELIVERY_CHANNEL), payload.capture());
            var json = context.getBean(JsonMapper.class).readTree(payload.getValue());
            assertThat(json.get("recipientId").asLong()).isEqualTo(9L);
            assertThat(json.get("message").get("createdAt").asText()).isEqualTo("2026-10-27T12:00:00.123Z");
            context.getBean(ChatRedisMessageListener.class).onMessage(redisMessage(payload.getValue()), null);

            verify(messaging).convertAndSendToUser("9", "/queue/messages", response);
        });
    }

    @Test
    void rejectsDeliveryWhenThereAreNoSubscribers() {
        contextRunner.run(context -> {
            StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);
            when(redis.convertAndSend(eq(RedisChatMessagePublisher.DELIVERY_CHANNEL), anyString()))
                    .thenReturn(0L);
            ChatSendResponse response = new ChatSendResponse("client-1", "message-1", "conversation-1",
                    7L, "Olá!", null, Instant.parse("2026-10-27T12:00:00Z"));

            assertThatThrownBy(() -> context.getBean(RedisChatMessagePublisher.class).publish(9L, response))
                    .isInstanceOf(ChatDeliveryUnavailableException.class);
        });
    }

    @Test
    void ignoresMalformedOrIncompleteRedisEvents() {
        contextRunner.run(context -> {
            ChatRedisMessageListener listener = context.getBean(ChatRedisMessageListener.class);
            listener.onMessage(redisMessage("invalid-json"), null);
            listener.onMessage(redisMessage("{}"), null);

            verifyNoInteractions(context.getBean(SimpMessagingTemplate.class));
        });
    }

    private Message redisMessage(String body) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        return message;
    }
}
