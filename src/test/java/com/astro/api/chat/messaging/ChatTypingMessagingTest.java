package com.astro.api.chat.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.astro.api.chat.dto.response.ChatTypingResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.json.JsonMapper;

class ChatTypingMessagingTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(SimpMessagingTemplate.class, () -> mock(SimpMessagingTemplate.class))
            .withUserConfiguration(RedisChatTypingPublisher.class, ChatRedisTypingListener.class);

    @Test
    void deliversAcrossRedisOnlyToRecipientAndDropsExpiredSignals() {
        runner.run(context -> {
            StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);
            SimpMessagingTemplate messaging = context.getBean(SimpMessagingTemplate.class);
            ValueOperations<String, String> values = mock(ValueOperations.class);
            when(redis.opsForValue()).thenReturn(values);
            when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
            ChatTypingResponse event = new ChatTypingResponse("conversation", 1L, true, Instant.now().plusSeconds(5));
            context.getBean(RedisChatTypingPublisher.class).publish(2L, event);
            ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
            verify(redis).convertAndSend(eq(RedisChatTypingPublisher.TYPING_CHANNEL), payload.capture());
            ChatRedisTypingListener listener = context.getBean(ChatRedisTypingListener.class);
            listener.onMessage(message(payload.getValue()), null);
            verify(messaging).convertAndSendToUser("2", "/queue/typing", event);
            clearInvocations(messaging);
            String expired = context.getBean(JsonMapper.class).writeValueAsString(new ChatTypingDeliveryEvent(2L,
                    new ChatTypingResponse("conversation", 1L, true, Instant.now().minusSeconds(1))));
            listener.onMessage(message(expired), null);
            listener.onMessage(message("bad-json"), null);
            verifyNoInteractions(messaging);
        });
    }

    @Test
    void throttlesRepeatedStartsAndAlwaysAllowsStop() {
        runner.run(context -> {
            StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);
            ValueOperations<String, String> values = mock(ValueOperations.class);
            when(redis.opsForValue()).thenReturn(values);
            when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);
            RedisChatTypingPublisher publisher = context.getBean(RedisChatTypingPublisher.class);
            publisher.publish(2L, new ChatTypingResponse("conversation", 1L, true, Instant.now().plusSeconds(5)));
            verify(redis, never()).convertAndSend(anyString(), anyString());
            publisher.publish(2L, new ChatTypingResponse("conversation", 1L, false, Instant.now()));
            verify(redis).delete("chat:typing:throttle:1:2");
            verify(redis).convertAndSend(eq(RedisChatTypingPublisher.TYPING_CHANNEL), anyString());
        });
    }

    private Message message(String payload) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(payload.getBytes(StandardCharsets.UTF_8));
        return message;
    }
}
