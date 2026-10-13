package com.astro.api.manager.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.astro.api.user.model.UserStatus;

public record ManagerSummaryResponse(
        @JsonProperty("id_usuario") Long id,
        @JsonProperty("nome") String name,
        String email,
        @JsonProperty("unidade_id") Long unitId,
        @JsonProperty("unidade") String unitName,
        UserStatus status) { }
