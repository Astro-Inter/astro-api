package com.astro.api.conformity.mapper;

import com.astro.api.conformity.dto.response.ConformityResponse;
import com.astro.api.conformity.repository.ConformityProjection;
import com.astro.api.conformity.model.Conformity;
import com.astro.api.conformity.dto.request.CreateConformityRequest;
import com.astro.api.conformity.dto.request.UpdateConformityRequest;
import com.astro.api.conformity.model.ConformityImportRow;
import com.astro.api.conformity.dto.response.ConformityImportResponse.RejectedRow;
import org.springframework.stereotype.Component;
import static com.astro.api.user.mapper.UserAvatarMapper.initials;

@Component
public class ConformityMapper {
    public RejectedRow toRejectedRow(ConformityImportRow row, String reason) {
        return new RejectedRow(
                row.row(), row.cpfText(), row.nrText(), reason);
    }
    public void applyUpdate(Conformity entity, UpdateConformityRequest request) {
        entity.setNrId(request.nrId()); entity.setExpiresAt(request.expiresAt());
    }
    public Conformity toNewManual(CreateConformityRequest request) {
        Conformity entity = new Conformity();
        entity.setCollaboratorId(request.collaboratorId()); entity.setNrId(request.nrId());
        entity.setExpiresAt(request.expiresAt()); entity.setApplicable(true); entity.setOrigin("MANUAL");
        entity.setEventCompletionId(null);
        return entity;
    }
    public ConformityResponse toResponse(ConformityProjection row, String profilePhotoUrl) {
        return new ConformityResponse(row.getId(), row.getCollaboratorId(), row.getCollaboratorName(),
                row.getCollaboratorEmail(), row.getNrId(), row.getNrTitle(), row.getExpiresAt(), row.getOrigin(),
                Boolean.TRUE.equals(row.getApplicable()), row.getEventCompletionId(), row.getStatus(),
                profilePhotoUrl, initials(row.getCollaboratorName()));
    }
}
