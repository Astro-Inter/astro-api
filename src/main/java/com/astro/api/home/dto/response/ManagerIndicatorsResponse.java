package com.astro.api.home.dto.response;


public record ManagerIndicatorsResponse(
        long currentEvents,
        long pendingValidations,
        long collaboratorsWithPendingEvents
) {
}
