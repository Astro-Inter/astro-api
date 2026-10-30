package com.astro.api.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.astro.api.auth.security.Role;
import com.astro.api.chat.dto.request.ChatTypingRequest;
import com.astro.api.chat.dto.response.ChatTypingResponse;
import com.astro.api.chat.mapper.ChatTypingMapper;
import com.astro.api.chat.model.ChatTypingTarget;
import com.astro.api.chat.security.ChatPrincipal;
import com.astro.api.common.exception.BusinessException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChatTypingServiceTest {
    private final ChatService chat = mock(ChatService.class);
    private final ChatTypingPublisher publisher = mock(ChatTypingPublisher.class);
    private final ChatTypingService service = new ChatTypingService(chat, new ChatTypingMapper(), publisher);
    private final ChatPrincipal principal = new ChatPrincipal(1L, 10L, Role.COLABORADOR, "uid");

    @Test
    void publishesAuthenticatedIdentityAndShortExpiry() {
        when(chat.authorizeTyping(principal, 2L, null)).thenReturn(new ChatTypingTarget("conversation", 2L));
        ChatTypingRequest request = new ChatTypingRequest();
        request.setRecipientId(2L); request.setTyping(true);
        Instant before = Instant.now();
        service.notifyTyping(principal, request);
        ArgumentCaptor<ChatTypingResponse> event = ArgumentCaptor.forClass(ChatTypingResponse.class);
        verify(publisher).publish(eq(2L), event.capture());
        assertEquals(1L, event.getValue().senderId());
        assertTrue(event.getValue().expiresAt().isAfter(before.plusSeconds(4)));
        assertFalse(event.getValue().expiresAt().isAfter(Instant.now().plusSeconds(5)));
    }

    @Test
    void rejectsMissingBooleanAndAmbiguousTarget() {
        ChatTypingRequest request = new ChatTypingRequest(); request.setRecipientId(2L);
        assertThrows(BusinessException.class, () -> service.notifyTyping(principal, request));
        request.setTyping(true); request.setConversationId("conversation");
        assertThrows(BusinessException.class, () -> service.notifyTyping(principal, request));
        verifyNoInteractions(chat, publisher);
    }

    @Test
    void deniedConversationNeverPublishes() {
        when(chat.authorizeTyping(principal, null, "conversation"))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("denied"));
        ChatTypingRequest request = new ChatTypingRequest(); request.setConversationId("conversation"); request.setTyping(false);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.notifyTyping(principal, request));
        verifyNoInteractions(publisher);
    }
}
