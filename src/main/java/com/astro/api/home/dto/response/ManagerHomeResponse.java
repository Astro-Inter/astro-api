package com.astro.api.home.dto.response;


import java.util.List;

public record ManagerHomeResponse(
        String username,
        String profilePhotoUrl,
        String workspaceName,
        List<PendingValidationResponse> pendingValidations,
        List<PendingFormResponse> pendingForms,
        ManagerIndicatorsResponse indicators
) {
}
