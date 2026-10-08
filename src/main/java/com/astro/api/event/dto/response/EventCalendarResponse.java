package com.astro.api.event.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record EventCalendarResponse(
        Long eventId,
        String eventTitle,
        Long eventClassId,
        String className,
        LocalDateTime startDate,
        LocalDateTime endDate,
        List<EventParticipantResponse> participants
) {
}
