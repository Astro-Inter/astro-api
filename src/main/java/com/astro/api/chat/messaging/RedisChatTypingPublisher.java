package com.astro.api.chat.messaging;

import com.astro.api.chat.dto.response.ChatTypingResponse;
import com.astro.api.chat.service.ChatTypingPublisher;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class RedisChatTypingPublisher implements ChatTypingPublisher {
    public static final String TYPING_CHANNEL = "astro:chat:typing:v1";
    private final StringRedisTemplate redis;
    private final JsonMapper mapper;

    public RedisChatTypingPublisher(StringRedisTemplate redis, JsonMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    @Override
    public void publish(Long recipientId, ChatTypingResponse event) {
        String key = "chat:typing:throttle:" + event.senderId() + ":" + recipientId;
        if (event.typing()) {
            Boolean allowed = redis.opsForValue().setIfAbsent(key, "1", Duration.ofMillis(1500));
            if (!Boolean.TRUE.equals(allowed)) return;
        } else {
            redis.delete(key);
        }
        redis.convertAndSend(TYPING_CHANNEL, mapper.writeValueAsString(new ChatTypingDeliveryEvent(recipientId, event)));
    }
}
