package com.astro.api.home.dto.response;


public record PendingValidationResponse(
        Long id,
        Long participantId,
        Long eventId,
        String collaboratorName,
        String eventTitle,
        boolean viewed
) {
}
