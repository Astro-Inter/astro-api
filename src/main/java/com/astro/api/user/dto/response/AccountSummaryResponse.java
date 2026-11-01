package com.astro.api.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AccountSummaryResponse(
        @Schema(example = "Maria Rosa", nullable = true) String name,
        @Schema(example = "maria@example.com") String email,
        @Schema(description = "URL temporária da foto; null quando ausente", nullable = true) String profilePhotoUrl) {
}
