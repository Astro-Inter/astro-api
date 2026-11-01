package com.astro.api.chat.messaging;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ChatRedisTypingListener implements MessageListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatRedisTypingListener.class);
    private final JsonMapper mapper;
    private final SimpMessagingTemplate messaging;

    public ChatRedisTypingListener(JsonMapper mapper, SimpMessagingTemplate messaging) {
        this.mapper = mapper;
        this.messaging = messaging;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            ChatTypingDeliveryEvent delivery = mapper.readValue(message.getBody(), ChatTypingDeliveryEvent.class);
            if (delivery.recipientId() == null || delivery.recipientId() <= 0 || delivery.event() == null
                    || delivery.event().expiresAt() == null || delivery.event().senderId() == null
                    || delivery.event().senderId() <= 0 || delivery.event().conversationId() == null) return;
            if (delivery.event().typing() && !delivery.event().expiresAt().isAfter(Instant.now())) return;
            messaging.convertAndSendToUser(delivery.recipientId().toString(), "/queue/typing", delivery.event());
        } catch (RuntimeException exception) {
            LOGGER.warn("Evento temporário de digitação inválido");
        }
    }
}
