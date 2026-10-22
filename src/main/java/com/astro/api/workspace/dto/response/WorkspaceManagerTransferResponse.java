package com.astro.api.workspace.dto.response;

public record WorkspaceManagerTransferResponse(Long workspaceId, Long previousManagerId, Long newManagerId) { }
