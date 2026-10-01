package com.astro.api.workspace.dto.response;

import java.util.List;

public record RegisterWorkspaceResponse(Long workspaceId, List<ImportErrorResponse> importErrors) { }
