package com.astro.api.workspace.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.workspace.dto.response.WorkspaceResponse;
import com.astro.api.workspace.mapper.WorkspaceMapper;
import com.astro.api.workspace.model.Workspace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceSettingsService {
    private final WorkspaceAccessService workspaceAccessService;
    private final WorkspaceMapper workspaceMapper;

    public WorkspaceSettingsService(WorkspaceAccessService workspaceAccessService, WorkspaceMapper workspaceMapper) {
        this.workspaceAccessService = workspaceAccessService;
        this.workspaceMapper = workspaceMapper;
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse find(AuthenticatedUser user) {
        Workspace workspace = workspaceAccessService.requireWorkspace(user, false);
        return workspaceMapper.toResponse(workspace);
    }

}
