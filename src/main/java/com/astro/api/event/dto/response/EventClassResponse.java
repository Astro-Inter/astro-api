package com.astro.api.event.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record EventClassResponse(
        Long id,
        String name,
        LocalDateTime startDate,
        LocalDateTime endDate,
        List<EventParticipantResponse> participants
) {
}
