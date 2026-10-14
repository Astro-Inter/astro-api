package com.astro.api.home.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ManagerIndicatorsResponse(
        @JsonProperty("current_events") long currentEvents,
        @JsonProperty("pending_validations") long pendingValidations,
        @JsonProperty("collaborators_with_pending_events") long collaboratorsWithPendingEvents
) {
}
