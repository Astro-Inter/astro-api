package com.astro.api.chat.service;

import com.astro.api.chat.dto.request.ChatTypingRequest;
import com.astro.api.chat.dto.response.ChatTypingResponse;
import com.astro.api.chat.mapper.ChatTypingMapper;
import com.astro.api.chat.model.ChatTypingTarget;
import com.astro.api.chat.security.ChatPrincipal;
import com.astro.api.common.exception.BusinessException;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

@Service
public class ChatTypingService {
    private final ChatService chatService;
    private final ChatTypingMapper mapper;
    private final ChatTypingPublisher publisher;

    public ChatTypingService(ChatService chatService, ChatTypingMapper mapper, ChatTypingPublisher publisher) {
        this.chatService = chatService;
        this.mapper = mapper;
        this.publisher = publisher;
    }

    public void notifyTyping(ChatPrincipal principal, ChatTypingRequest request) {
        if (request == null || request.getTyping() == null) throw new BusinessException("Informe typing");
        if (principal == null) throw new AccessDeniedException("Sessão não autenticada");
        boolean hasRecipient = request.getRecipientId() != null;
        boolean hasConversation = request.getConversationId() != null;
        if (hasRecipient == hasConversation) throw new BusinessException("Informe recipientId ou conversationId");
        ChatTypingTarget target = chatService.authorizeTyping(principal, request.getRecipientId(), request.getConversationId());
        ChatTypingResponse event = mapper.toResponse(target, principal.collaboratorId(), request.getTyping(), Instant.now());
        publisher.publish(target.recipientId(), event);
    }
}
