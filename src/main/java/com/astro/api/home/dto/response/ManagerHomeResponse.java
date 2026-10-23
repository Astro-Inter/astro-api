package com.astro.api.home.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ManagerHomeResponse(
        String username,
        String profilePhotoUrl,
        @JsonProperty("workspace_name") String workspaceName,
        @JsonProperty("pending_validations") List<PendingValidationResponse> pendingValidations,
        @JsonProperty("pending_forms") List<PendingFormResponse> pendingForms,
        ManagerIndicatorsResponse indicators
) {
}
