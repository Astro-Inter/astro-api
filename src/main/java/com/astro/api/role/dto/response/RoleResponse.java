package com.astro.api.role.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RoleResponse(
        @JsonProperty("id_cargo") Long roleId,
        @JsonProperty("id_workspace") Long workspaceId,
        @JsonProperty("nome") String name,
        @JsonProperty("quantidade_colaboradores") long collaboratorCount,
        String status
) {
}
