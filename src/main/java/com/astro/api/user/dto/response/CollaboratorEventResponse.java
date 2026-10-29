package com.astro.api.user.dto.response;

import com.astro.api.event.model.EventStatus;
import com.astro.api.event.model.CompletionMode;
import com.astro.api.event.model.ParticipationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "ManagedCollaboratorEventResponse")
public record CollaboratorEventResponse(
        Long eventId,
        String eventTitle,
        LocalDateTime classLimitDate,
        ParticipationStatus completionStatus,
        EventStatus eventStatus,
        CompletionMode completionMode) {
}
