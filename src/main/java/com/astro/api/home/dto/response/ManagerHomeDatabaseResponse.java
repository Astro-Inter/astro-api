package com.astro.api.home.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public record ManagerHomeDatabaseResponse(
        String username,
        String workspaceName,
        List<ManagerPendingValidationDatabaseResponse> pendingValidations,
        ManagerIndicatorsResponse indicators
) {
    public record ManagerPendingValidationDatabaseResponse(
            Long id,
            @JsonAlias("user_id") Long participantId,
            Long eventId,
            String collaboratorName,
            String eventTitle
    ) {
    }
}
