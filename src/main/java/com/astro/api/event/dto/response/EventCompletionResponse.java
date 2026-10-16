package com.astro.api.event.dto.response;

import com.astro.api.event.model.EventCompletionStatus;

import java.time.LocalDateTime;

public record EventCompletionResponse(Long id, EventCompletionStatus status, LocalDateTime completionDate) {
}
