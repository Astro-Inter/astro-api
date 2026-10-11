package com.astro.api.cargo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CargoResponse(
        @JsonProperty("id_cargo") Long idCargo,
        @JsonProperty("id_workspace") Long idWorkspace,
        String nome,
        @JsonProperty("quantidade_colaboradores") long quantidadeColaboradores,
        String status
) {
}
