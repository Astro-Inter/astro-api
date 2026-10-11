package com.astro.api.event.dto.response;

import java.time.LocalDateTime;

public record EventCalendarResponse(
        Long eventId,
        String eventTitle,
        Long eventClassId,
        String className,
        LocalDateTime startDate,
        LocalDateTime endDate
) {
}
