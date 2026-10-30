package com.astro.api.home.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PendingValidationResponse(
        Long id,
        Long participantId,
        Long eventId,
        @JsonProperty("collaborator_name") String collaboratorName,
        @JsonProperty("event_title") String eventTitle,
        boolean viewed
) {
}
