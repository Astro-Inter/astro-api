package com.astro.api.home.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WorkspaceHomeDatabaseResponse(
        @JsonProperty("nome_usuario") String userName,
        @JsonProperty("estatisticas") WorkspaceHomeStatisticsResponse statistics
) {
}
