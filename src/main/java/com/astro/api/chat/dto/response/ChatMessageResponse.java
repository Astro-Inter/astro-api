package com.astro.api.chat.dto.response;

import java.time.Instant;

public record ChatMessageResponse(
        String id,
        String conversationId,
        Long senderId,
        String content,
        String replyToMessageId,
        Instant createdAt
) { }
