package com.astro.api.manager.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.WorkModel;

public record ManagerDetailsResponse(
        @JsonProperty("id_usuario") Long id,
        @JsonProperty("nome") String name,
        String cpf, String email,
        @JsonProperty("modalidade") WorkModel workModel,
        UserStatus status,
        @JsonProperty("cargo_id") Long roleId,
        @JsonProperty("cargo") String roleName,
        @JsonProperty("unidade_id") Long unitId,
        @JsonProperty("unidade") String unitName,
        @JsonProperty("workspace_id") Long workspaceId) { }
