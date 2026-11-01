package com.astro.api.chat.security;

import java.util.Set;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

public class ChatCapabilitiesInterceptor implements ChannelInterceptor {
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.CONNECT_ACK) return message;
        Message<?> connect = (Message<?>) message.getHeaders().get(StompHeaderAccessor.CONNECT_MESSAGE_HEADER);
        if (connect == null) return message;
        StompHeaderAccessor original = MessageHeaderAccessor.getAccessor(connect, StompHeaderAccessor.class);
        if (original == null) return message;
        Set<String> versions = original.getAcceptVersion();
        String version = null;
        for (String candidate : new String[]{"1.2", "1.1", "1.0"}) {
            if (versions.contains(candidate)) { version = candidate; break; }
        }
        if (version == null) return message;
        StompHeaderAccessor connected = StompHeaderAccessor.create(StompCommand.CONNECTED);
        connected.setSessionId(SimpMessageHeaderAccessor.getSessionId(message.getHeaders()));
        connected.setVersion(version);
        long[] heartbeat = (long[]) message.getHeaders().get(SimpMessageHeaderAccessor.HEART_BEAT_HEADER);
        if (heartbeat != null) connected.setHeartbeat(heartbeat[0], heartbeat[1]);
        connected.setNativeHeader("astro-typing", "1");
        return MessageBuilder.createMessage(message.getPayload(), connected.getMessageHeaders());
    }
}
