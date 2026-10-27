package com.astro.api.home.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record WorkspaceHomeResponse(
        @JsonProperty("nome_usuario") String userName,
        @JsonProperty("estatisticas") WorkspaceHomeStatisticsResponse statistics,
        @Schema(description = "URL temporária da foto da própria conta; null quando ausente ou indisponível", nullable = true)
        String profilePhotoUrl
) {
}
