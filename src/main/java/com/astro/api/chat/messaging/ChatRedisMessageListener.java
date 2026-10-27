package com.astro.api.chat.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ChatRedisMessageListener implements MessageListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatRedisMessageListener.class);

    private final JsonMapper jsonMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatRedisMessageListener(JsonMapper jsonMapper, SimpMessagingTemplate messagingTemplate) {
        this.jsonMapper = jsonMapper;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            ChatDeliveryEvent event = jsonMapper.readValue(message.getBody(), ChatDeliveryEvent.class);
            if (event.recipientId() == null || event.message() == null) {
                LOGGER.warn("Evento Redis de entrega do Chat sem destinatário ou mensagem");
                return;
            }
            messagingTemplate.convertAndSendToUser(event.recipientId().toString(), "/queue/messages", event.message());
        } catch (RuntimeException exception) {
            LOGGER.error("Falha ao processar evento Redis de entrega do Chat", exception);
        }
    }
}
