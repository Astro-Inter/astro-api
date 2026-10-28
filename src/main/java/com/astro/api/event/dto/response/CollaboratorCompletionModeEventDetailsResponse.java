package com.astro.api.event.dto.response;

import com.astro.api.event.model.CompletionMode;

import java.time.LocalDateTime;

public record CollaboratorCompletionModeEventDetailsResponse(
        Long eventId,
        String eventTitle,
        String eventDescription,
        String externalLink,
        long participantCount,
        LocalDateTime lastClassLimitDate,
        CompletionMode completionMode,
        Long managerId,
        String managerName,
        long pendingValidationCount,
        long validatedCount,
        long notSubmittedCount
) {
}
