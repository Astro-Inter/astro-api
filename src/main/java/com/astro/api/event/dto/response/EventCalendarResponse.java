package com.astro.api.event.dto.response;

import java.time.LocalDateTime;
import com.astro.api.event.model.EventStatus;

public record EventCalendarResponse(
        Long eventId,
        String eventTitle,
        Long eventClassId,
        String className,
        LocalDateTime startDate,
        LocalDateTime endDate,
        EventStatus status
) {
}
