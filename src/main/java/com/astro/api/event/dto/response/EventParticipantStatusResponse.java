package com.astro.api.event.dto.response;

import com.astro.api.event.model.ParticipationStatus;

public record EventParticipantStatusResponse(
        Long participantId,
        String participantName,
        String eventTitle,
        ParticipationStatus participationStatus,
        String profilePhotoUrl
) {
}
