package com.astro.api.home.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ManagerHomeDatabaseResponse(
        String username,
        @JsonProperty("workspace_name") String workspaceName,
        @JsonProperty("pending_validations") List<ManagerPendingValidationDatabaseResponse> pendingValidations,
        ManagerIndicatorsResponse indicators
) {
    public record ManagerPendingValidationDatabaseResponse(
            Long id,
            @JsonProperty("collaborator_name") String collaboratorName,
            @JsonProperty("event_title") String eventTitle
    ) {
    }
}
