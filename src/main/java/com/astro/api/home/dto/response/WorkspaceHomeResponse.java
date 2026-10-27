package com.astro.api.home.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record WorkspaceHomeResponse(
        String userName,
        WorkspaceHomeStatisticsResponse statistics,
        @Schema(description = "URL temporária da foto da própria conta; null quando ausente ou indisponível", nullable = true)
        String profilePhotoUrl
) {
}
