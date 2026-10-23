package com.astro.api.event.dto.response;

import com.astro.api.event.model.CompletionMode;
import com.astro.api.event.model.EventStatus;
import com.astro.api.event.model.ParticipationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CollaboratorEventDetailsResponse(
        Long eventId,
        String eventTitle,
        LocalDateTime classLimitDate,
        ParticipationStatus participationStatus,
        EventStatus eventStatus,
        String classStatus,
        String eventDescription,
        LocalDate complianceValidity,
        String externalLink,
        Long managerId,
        String managerName,
        boolean evidenceMandatory,
        CompletionMode completionMode,
        List<EventEvidenceResponse> evidences
) {
}
