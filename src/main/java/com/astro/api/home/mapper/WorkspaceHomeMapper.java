package com.astro.api.home.mapper;

import com.astro.api.home.dto.response.WorkspaceHomeDatabaseResponse;
import com.astro.api.home.dto.response.WorkspaceHomeResponse;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceHomeMapper {
    public WorkspaceHomeResponse toResponse(WorkspaceHomeDatabaseResponse home, String profilePhotoUrl) {
        return new WorkspaceHomeResponse(home.userName(), home.statistics(), profilePhotoUrl);
    }
}
