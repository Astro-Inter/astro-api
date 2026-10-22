package com.astro.api.event.dto.response;

import com.astro.api.event.model.EventStatus;
import com.astro.api.event.model.ParticipationStatus;

import java.time.LocalDateTime;

public record CollaboratorEventResponse(
        Long id,
        String eventTitle,
        LocalDateTime classLimitDate,
        boolean viewed,
        ParticipationStatus participationStatus,
        String className,
        EventStatus eventStatus
) {
}
