package com.astro.api.role.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RoleNrOptionResponse(
        Integer nr,
        @JsonProperty("descricao") String description,
        @JsonProperty("ativo") boolean active
) {
}
