package com.astro.api.chat.messaging;

import com.astro.api.chat.dto.response.ChatSendResponse;
import com.astro.api.chat.service.ChatDeliveryUnavailableException;
import com.astro.api.chat.service.ChatMessagePublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisChatMessagePublisher implements ChatMessagePublisher {
    public static final String DELIVERY_CHANNEL = "astro:chat:delivery:v1";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisChatMessagePublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(Long recipientId, ChatSendResponse message) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(new ChatDeliveryEvent(recipientId, message));
        } catch (JsonProcessingException exception) {
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
