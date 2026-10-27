package com.astro.api.chat.messaging;

import com.astro.api.chat.dto.response.ChatSendResponse;
import com.astro.api.chat.service.ChatDeliveryUnavailableException;
import com.astro.api.chat.service.ChatMessagePublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class RedisChatMessagePublisher implements ChatMessagePublisher {
    public static final String DELIVERY_CHANNEL = "astro:chat:delivery:v1";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;

    public RedisChatMessagePublisher(StringRedisTemplate redisTemplate, JsonMapper jsonMapper) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void publish(Long recipientId, ChatSendResponse message) {
        String payload;
        try {
            payload = jsonMapper.writeValueAsString(new ChatDeliveryEvent(recipientId, message));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Não foi possível serializar evento de entrega do Chat", exception);
        }
        try {
            Long subscribers = redisTemplate.convertAndSend(DELIVERY_CHANNEL, payload);
            if (subscribers == null || subscribers == 0) {
                throw new ChatDeliveryUnavailableException(null);
            }
        } catch (DataAccessException exception) {
            throw new ChatDeliveryUnavailableException(exception);
        }
    }
}
