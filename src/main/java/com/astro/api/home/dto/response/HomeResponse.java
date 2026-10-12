package com.astro.api.home.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HomeResponse(
        @JsonProperty("nome_usuario") String userName,
        @JsonProperty("estatisticas") HomeStatisticsResponse statistics
) {
}
