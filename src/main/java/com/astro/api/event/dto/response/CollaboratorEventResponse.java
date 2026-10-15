package com.astro.api.event.dto.response;

import com.astro.api.event.model.EventStatus;

import java.time.LocalDateTime;

public record CollaboratorEventResponse(
        Long id,
        String eventTitle,
        LocalDateTime classLimitDate,
        boolean viewed,
        String classStatus,
        String className,
        EventStatus eventStatus
) {
}
