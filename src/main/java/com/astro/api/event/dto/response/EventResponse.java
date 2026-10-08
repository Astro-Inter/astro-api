package com.astro.api.event.dto.response;

import com.astro.api.event.model.CompletionMode;
import com.astro.api.event.model.EventStatus;

import java.time.Instant;
import java.util.List;

public record EventResponse(
        Long id,
        String title,
        String description,
        Integer nrId,
        String externalLink,
        CompletionMode completionMode,
        boolean mandatoryEvidence,
        EventStatus status,
        Instant cancellationDate,
        String cancellationReason,
        List<EventClassResponse> eventClasses
) {
}
