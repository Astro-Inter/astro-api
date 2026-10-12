package com.astro.api.unit.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UnitNrResponse(Integer nr, @JsonProperty("descricao") String description) {
}
