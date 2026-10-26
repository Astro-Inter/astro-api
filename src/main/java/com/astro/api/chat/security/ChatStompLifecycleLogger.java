package com.astro.api.chat.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class ChatStompLifecycleLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatStompLifecycleLogger.class);

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        if (event.getUser() instanceof ChatPrincipal principal) {
            LOGGER.info("Conexão STOMP encerrada para colaborador {}", principal.getName());
        }
    }
}
