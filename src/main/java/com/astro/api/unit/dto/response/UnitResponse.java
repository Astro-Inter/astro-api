package com.astro.api.unit.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UnitResponse(
        @JsonProperty("id_unidade") Long unitId,
        @JsonProperty("id_workspace") Long workspaceId,
        @JsonProperty("nome") String name,
        String status,
        @JsonProperty("quantidade_colaboradores") long collaboratorCount,
        @JsonProperty("cep") String postalCode,
        @JsonProperty("rua") String street,
        @JsonProperty("cidade") String city,
        @JsonProperty("bairro") String neighborhood,
        @JsonProperty("estado") String state,
        @JsonProperty("complemento") String addressLine2
) {
}
