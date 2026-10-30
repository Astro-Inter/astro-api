package com.astro.api.home.dto.response;


import java.util.List;

public record ManagerHomeDatabaseResponse(
        String username,
        String workspaceName,
        List<ManagerPendingValidationDatabaseResponse> pendingValidations,
        ManagerIndicatorsResponse indicators
) {
    public record ManagerPendingValidationDatabaseResponse(
            Long id,
            String collaboratorName,
            String eventTitle
    ) {
    }
}
