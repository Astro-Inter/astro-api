package com.astro.api.manager.mapper;

import com.astro.api.manager.dto.response.ManagerSummaryResponse;
import com.astro.api.manager.dto.response.ManagerDetailsResponse;
import com.astro.api.manager.repository.ManagerDetailsProjection;
import com.astro.api.user.model.WorkModel;
import com.astro.api.manager.repository.ManagerSummaryProjection;
import com.astro.api.user.model.UserStatus;
import org.springframework.stereotype.Component;

@Component
public class ManagerMapper {
    public ManagerSummaryResponse toSummary(ManagerSummaryProjection row) {
        return new ManagerSummaryResponse(row.getId(), row.getName(), row.getEmail(),
                row.getUnitId(), row.getUnitName(), UserStatus.valueOf(row.getStatus()));
    }

    public ManagerDetailsResponse toDetails(ManagerDetailsProjection row) {
        return new ManagerDetailsResponse(row.getId(), row.getName(), row.getCpf(), row.getEmail(),
                row.getWorkModel() == null ? null : WorkModel.valueOf(row.getWorkModel()),
                UserStatus.valueOf(row.getStatus()), row.getRoleId(), row.getRoleName(),
                row.getUnitId(), row.getUnitName(), row.getWorkspaceId());
    }
}
