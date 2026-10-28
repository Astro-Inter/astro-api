package com.astro.api.event.dto.response;

import com.astro.api.event.model.EventCompletionStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ManagerCollaboratorCompletionDetailsResponse(
        String profilePhotoUrl,
        String participantName,
        String roleName,
        String unitName,
        EventCompletionStatus status,
        LocalDateTime completionDate,
        LocalDateTime endDate,
        boolean evidenceMandatory,
        List<EventEvidenceResponse> evidences,
        String eventTitle
) {
}
