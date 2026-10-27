package com.astro.api.event.dto.response;

import com.astro.api.event.model.CompletionMode;

import java.time.LocalDateTime;
import java.util.List;

public record ManagerCompletionModeEventDetailsResponse(
        Long eventId,
        String eventTitle,
        String eventDescription,
        String externalLink,
        List<EventParticipantResponse> participants,
        LocalDateTime lastClassLimitDate,
        CompletionMode completionMode,
        Long managerId,
        String managerName
) {
}
