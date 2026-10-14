package com.astro.api.conformity.mapper;

import com.astro.api.conformity.dto.response.ConformityResponse;
import com.astro.api.conformity.repository.ConformityProjection;
import com.astro.api.conformity.model.Conformity;
import com.astro.api.conformity.dto.request.CreateConformityRequest;
import org.springframework.stereotype.Component;

@Component
public class ConformityMapper {
    public Conformity toNewManual(CreateConformityRequest request) {
        Conformity entity = new Conformity();
        entity.setCollaboratorId(request.collaboratorId()); entity.setNrId(request.nrId());
        entity.setExpiresAt(request.expiresAt()); entity.setApplicable(true); entity.setOrigin("MANUAL");
        entity.setEventCompletionId(null);
        return entity;
    }
    public ConformityResponse toResponse(ConformityProjection row) {
        return new ConformityResponse(row.getId(), row.getCollaboratorId(), row.getCollaboratorName(),
                row.getCollaboratorEmail(), row.getNrId(), row.getNrTitle(), row.getExpiresAt(), row.getOrigin(),
                Boolean.TRUE.equals(row.getApplicable()), row.getEventCompletionId(), row.getStatus());
    }
}
