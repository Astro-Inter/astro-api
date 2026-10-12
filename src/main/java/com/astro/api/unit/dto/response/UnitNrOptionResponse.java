package com.astro.api.unit.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UnitNrOptionResponse(Integer nr, @JsonProperty("descricao") String description,
                                   @JsonProperty("ativo") boolean active) {
}
