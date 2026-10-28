package com.astro.api.event.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ManagerCollaboratorCompletionDetailsResponse(
        String participantName,
        String roleName,
        String unitName,
        LocalDateTime completionDate,
        LocalDateTime endDate,
        boolean evidenceMandatory,
        List<EventEvidenceResponse> evidences,
        String eventTitle
) {
}
