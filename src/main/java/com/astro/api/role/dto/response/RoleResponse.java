package com.astro.api.role.dto.response;


public record RoleResponse(
        Long roleId,
        Long workspaceId,
        String name,
        long collaboratorCount,
        String status
) {
}
