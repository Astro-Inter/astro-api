package com.astro.api.chat.dto.response;

import java.time.Instant;

public record ChatTypingResponse(String conversationId, Long senderId, boolean typing, Instant expiresAt) { }
