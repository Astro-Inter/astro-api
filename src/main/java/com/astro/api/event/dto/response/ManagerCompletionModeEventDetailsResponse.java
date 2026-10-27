package com.astro.api.event.dto.response;

import com.astro.api.event.model.CompletionMode;

import java.time.LocalDateTime;

public record ManagerCompletionModeEventDetailsResponse(
        Long eventId,
        String eventTitle,
        String eventDescription,
        String externalLink,
        long participantCount,
        LocalDateTime lastClassLimitDate,
        CompletionMode completionMode,
        Long managerId,
        String managerName
) {
}
