package com.astro.api.chat.dto.response;

import java.util.List;

public record ChatMessagePageResponse(
        List<ChatMessageResponse> messages,
        String nextCursor,
        boolean hasMore
) { }
