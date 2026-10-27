package com.astro.api.chat.messaging;

import com.astro.api.chat.dto.response.ChatSendResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class ChatRedisMessageListener implements MessageListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatRedisMessageListener.class);

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatRedisMessageListener(ObjectMapper objectMapper, SimpMessagingTemplate messagingTemplate) {
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            ChatDeliveryEvent event = objectMapper.readValue(message.getBody(), ChatDeliveryEvent.class);
            if (event.recipientId() == null || event.message() == null) {
                LOGGER.warn("Evento Redis de entrega do Chat sem destinatário ou mensagem");
                return;
            }
            messagingTemplate.convertAndSendToUser(event.recipientId().toString(), "/queue/messages", event.message());
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Falha ao processar evento Redis de entrega do Chat", exception);
        }
    }
}
