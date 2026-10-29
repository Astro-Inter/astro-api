package com.astro.api.user.dto.response;

import java.util.List;

public record CollaboratorEventsResponse(
        Long collaboratorId,
        String collaboratorName,
        List<CollaboratorEventResponse> events) {
}
