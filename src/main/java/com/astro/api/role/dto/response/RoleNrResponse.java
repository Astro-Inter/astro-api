package com.astro.api.role.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RoleNrResponse(
        @JsonProperty("codigo_nr") Integer nrCode,
        @JsonProperty("descricao") String description
) {
}
