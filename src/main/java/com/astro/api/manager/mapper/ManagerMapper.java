package com.astro.api.manager.mapper;

import com.astro.api.manager.dto.response.ManagerSummaryResponse;
import com.astro.api.manager.repository.ManagerSummaryProjection;
import com.astro.api.user.model.UserStatus;
import org.springframework.stereotype.Component;

@Component
public class ManagerMapper {
    public ManagerSummaryResponse toSummary(ManagerSummaryProjection row) {
        return new ManagerSummaryResponse(row.getId(), row.getName(), row.getEmail(),
                row.getUnitId(), row.getUnitName(), UserStatus.valueOf(row.getStatus()));
    }
}
