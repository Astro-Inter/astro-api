package com.astro.api.chat.repository;

import java.time.Instant;

public record LastMessageProjection(
        String conversationId,
        Long senderId,
        String content,
        Instant date
) { }
