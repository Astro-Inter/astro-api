package com.astro.api.user.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.astro.api.user.model.UserStatus;

public record CollaboratorSummaryResponse(
        @JsonProperty("id_usuario") Long id,
        @JsonProperty("nome") String name,
        String email,
        @JsonProperty("cargo_id") Long roleId,
        @JsonProperty("cargo") String roleName,
        @JsonProperty("unidade_id") Long unitId,
        @JsonProperty("unidade") String unitName,
        UserStatus status) {
}
