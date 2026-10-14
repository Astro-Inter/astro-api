package com.astro.api.conformity.mapper;

import com.astro.api.conformity.dto.response.ConformityResponse;
import com.astro.api.conformity.repository.ConformityProjection;
import org.springframework.stereotype.Component;

@Component
public class ConformityMapper {
    public ConformityResponse toResponse(ConformityProjection row) {
        return new ConformityResponse(row.getId(), row.getCollaboratorId(), row.getCollaboratorName(),
                row.getCollaboratorEmail(), row.getNrId(), row.getNrTitle(), row.getExpiresAt(), row.getOrigin(),
                Boolean.TRUE.equals(row.getApplicable()), row.getEventCompletionId(), row.getStatus());
    }
}
