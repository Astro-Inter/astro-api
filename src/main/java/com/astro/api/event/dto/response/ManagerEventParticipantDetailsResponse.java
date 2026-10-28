package com.astro.api.event.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ManagerEventParticipantDetailsResponse(
        Long participantId,
        String profilePhotoUrl,
        String eventTitle,
        String participantName,
        String roleName,
        String unitName,
        LocalDateTime endDate,
        LocalDateTime completionDate,
        boolean evidenceMandatory,
        List<EventEvidenceResponse> evidences
) {
}
