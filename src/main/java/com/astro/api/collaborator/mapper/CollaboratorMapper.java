package com.astro.api.collaborator.mapper;

import com.astro.api.collaborator.dto.response.CollaboratorSummaryResponse;
import com.astro.api.collaborator.repository.CollaboratorSummaryProjection;
import com.astro.api.user.model.UserStatus;
import org.springframework.stereotype.Component;

@Component
public class CollaboratorMapper {
    public CollaboratorSummaryResponse toSummary(CollaboratorSummaryProjection row) {
        return new CollaboratorSummaryResponse(row.getId(), row.getName(), row.getEmail(),
                row.getRoleId(), row.getRoleName(), row.getUnitId(), row.getUnitName(),
                UserStatus.valueOf(row.getStatus()));
    }
}
