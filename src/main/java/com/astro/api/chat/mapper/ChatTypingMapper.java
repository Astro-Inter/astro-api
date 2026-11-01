package com.astro.api.chat.mapper;

import com.astro.api.chat.dto.response.ChatTypingResponse;
import com.astro.api.chat.model.ChatTypingTarget;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ChatTypingMapper {
    public ChatTypingResponse toResponse(ChatTypingTarget target, Long senderId, boolean typing, Instant now) {
        Instant expiresAt = now;
        if (typing) expiresAt = now.plusSeconds(5);
        return new ChatTypingResponse(target.conversationId(), senderId, typing, expiresAt);
    }
}
