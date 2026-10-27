package com.astro.api.event.dto.response;

import com.astro.api.event.model.CompletionMode;
import com.astro.api.event.model.EventStatus;

public record MyEventResponse(
        Long id,
        String title,
        EventStatus status,
        CompletionMode completionMode,
        Long pendingCount
) {
}
