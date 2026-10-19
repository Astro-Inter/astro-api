package com.astro.api.workspace.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.workspace.dto.request.UpdateWorkspaceRequest;
import com.astro.api.workspace.dto.response.WorkspaceResponse;
import com.astro.api.workspace.mapper.WorkspaceMapper;
import com.astro.api.workspace.model.Workspace;
import com.astro.api.workspace.repository.WorkspaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceSettingsService {
    private final WorkspaceAccessService workspaceAccessService;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMapper workspaceMapper;

    public WorkspaceSettingsService(WorkspaceAccessService workspaceAccessService, WorkspaceRepository workspaceRepository, WorkspaceMapper workspaceMapper) {
        this.workspaceAccessService = workspaceAccessService;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMapper = workspaceMapper;
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse find(AuthenticatedUser user) {
        Workspace workspace = workspaceAccessService.requireWorkspace(user, false);
        return workspaceMapper.toResponse(workspace);
    }

    @Transactional
    public WorkspaceResponse update(UpdateWorkspaceRequest body, AuthenticatedUser user) {
        Workspace workspace = workspaceAccessService.requireWorkspace(user, true);
        if (workspaceRepository.existsByCnpjAndIdNot(body.cnpj(), workspace.id)) {
            throw new ConflictException("Já existe outro workspace com este CNPJ");
        }
        workspaceMapper.applyUpdate(workspace, body);
        Workspace savedWorkspace = workspaceRepository.saveAndFlush(workspace);
        return workspaceMapper.toResponse(savedWorkspace);
    }
}
