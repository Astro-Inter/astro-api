package com.astro.api.user.dto.response;

import com.astro.api.event.model.EventStatus;
import com.astro.api.event.model.ParticipationStatus;

import java.time.LocalDateTime;

public record CollaboratorEventResponse(
        Long collaboratorId,
        String collaboratorName,
        Long eventId,
        String eventTitle,
        LocalDateTime classLimitDate,
        ParticipationStatus completionStatus,
        EventStatus eventStatus) {
}
