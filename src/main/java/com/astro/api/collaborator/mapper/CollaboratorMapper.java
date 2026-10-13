package com.astro.api.collaborator.mapper;

import com.astro.api.collaborator.dto.response.CollaboratorSummaryResponse;
import com.astro.api.collaborator.repository.CollaboratorSummaryProjection;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.WorkModel;
import com.astro.api.collaborator.dto.response.CollaboratorDetailsResponse;
import com.astro.api.collaborator.repository.CollaboratorDetailsProjection;
import org.springframework.stereotype.Component;

@Component
public class CollaboratorMapper {
    public CollaboratorDetailsResponse toDetails(CollaboratorDetailsProjection row) {
        return new CollaboratorDetailsResponse(row.getId(), row.getName(), row.getCpf(), row.getEmail(),
                row.getWorkModel() == null ? null : WorkModel.valueOf(row.getWorkModel()),
                UserStatus.valueOf(row.getStatus()), row.getRoleId(), row.getRoleName(),
                row.getUnitId(), row.getUnitName(), row.getWorkspaceId());
    }

    public CollaboratorSummaryResponse toSummary(CollaboratorSummaryProjection row) {
        return new CollaboratorSummaryResponse(row.getId(), row.getName(), row.getEmail(),
                row.getRoleId(), row.getRoleName(), row.getUnitId(), row.getUnitName(),
                UserStatus.valueOf(row.getStatus()));
    }
}
