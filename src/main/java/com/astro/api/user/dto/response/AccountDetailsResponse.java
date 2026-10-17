package com.astro.api.user.dto.response;

import com.astro.api.user.model.WorkModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AccountDetailsResponse(
        @JsonProperty("nome") @Schema(example = "Maria Rosa", nullable = true) String name,
        @Schema(example = "52998224725", nullable = true, description = "CPF real do banco; null se não cadastrado") String cpf,
        @Schema(example = "maria@example.com") String email,
        @Schema(example = "20", nullable = true) Long roleId,
        @JsonProperty("cargo") @Schema(example = "Engenheira", nullable = true) String roleName,
        @Schema(example = "10") Long unitId,
        @JsonProperty("unidade") @Schema(example = "Osasco") String unitName,
        @JsonProperty("modalidade") @Schema(nullable = true, example = "PRESENCIAL") WorkModel workModel,
        @Schema(description = "URL temporária da foto; null quando ausente", nullable = true) String profilePhotoUrl) {
}
