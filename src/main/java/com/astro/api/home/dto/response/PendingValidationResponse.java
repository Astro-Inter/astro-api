package com.astro.api.home.dto.response;


public record PendingValidationResponse(
        Long id,
        String collaboratorName,
        String eventTitle,
        boolean viewed
) {
}
