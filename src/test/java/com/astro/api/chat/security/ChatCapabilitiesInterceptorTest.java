package com.astro.api.chat.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.astro.api.auth.security.Role;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.messaging.StompSubProtocolHandler;

class ChatCapabilitiesInterceptorTest {
    @Test
    void realStompEncoderRetainsIdentityHeartbeatAndAdvertisesTyping() throws Exception {
        StompHeaderAccessor connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.setAcceptVersion("1.2");
        Message<byte[]> original = MessageBuilder.createMessage(new byte[0], connect.getMessageHeaders());
        SimpMessageHeaderAccessor ack = SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT_ACK);
        ack.setSessionId("session");
        ack.setHeader(StompHeaderAccessor.CONNECT_MESSAGE_HEADER, original);
        ack.setHeader(SimpMessageHeaderAccessor.HEART_BEAT_HEADER, new long[]{10000, 10000});
        Message<?> output = new ChatCapabilitiesInterceptor().preSend(
                MessageBuilder.createMessage(new byte[0], ack.getMessageHeaders()), mock(MessageChannel.class));
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session");
        when(session.isOpen()).thenReturn(true);
        when(session.getPrincipal()).thenReturn(new ChatPrincipal(1L, 10L, Role.COLABORADOR, "uid"));
        StompSubProtocolHandler handler = new StompSubProtocolHandler();
        handler.afterSessionStarted(session, mock(MessageChannel.class));
        handler.handleMessageToClient(session, output);
        ArgumentCaptor<WebSocketMessage<?>> sent = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session).sendMessage(sent.capture());
        String frame = ((TextMessage) sent.getValue()).getPayload();
        assertTrue(frame.startsWith("CONNECTED"));
        assertTrue(frame.contains("astro-typing:1"));
        assertTrue(frame.contains("user-name:1"));
        assertTrue(frame.contains("heart-beat:10000,10000"));
        assertTrue(frame.contains("version:1.2"));
    }
}
