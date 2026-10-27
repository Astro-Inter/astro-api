package com.astro.api.event.dto.response;

import com.astro.api.event.model.ParticipationStatus;

public record EventParticipantStatusResponse(
        String participantName,
        String eventTitle,
        ParticipationStatus participationStatus
) {
}
