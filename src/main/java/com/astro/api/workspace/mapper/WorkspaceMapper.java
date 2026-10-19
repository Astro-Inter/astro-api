package com.astro.api.workspace.mapper;

import com.astro.api.workspace.dto.request.WorkspaceRequest;
import com.astro.api.workspace.dto.response.WorkspaceResponse;
import com.astro.api.workspace.model.Workspace;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceMapper {
    public WorkspaceResponse toResponse(Workspace workspace) {
        return new WorkspaceResponse(workspace.id, workspace.name, workspace.cnpj);
    }

    public Workspace toEntity(WorkspaceRequest request) {
        Workspace workspace = new Workspace();
        workspace.name = request.name().trim();
        workspace.cnpj = request.cnpj();
        return workspace;
    }
}
