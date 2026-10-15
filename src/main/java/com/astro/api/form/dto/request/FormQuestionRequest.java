package com.astro.api.form.dto.request;

import com.astro.api.form.model.FormQuestionType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FormQuestionRequest(
        @JsonProperty("id_pergunta") @Positive Long id,
        @JsonProperty("tipo") @NotNull FormQuestionType type,
        @JsonProperty("pergunta") @Size(max = 140) String question,
        @JsonProperty("obrigatoria") Boolean required,
        @JsonProperty("multipla_escolha") Boolean multiple,
        @JsonProperty("opcoes") @Size(max = 100) List<@NotNull @Valid FormOptionRequest> options,
        @JsonProperty("maximo_arquivos") @Min(1) @Max(10) Integer maxFiles,
        @JsonProperty("tamanho_maximo_mb") @Min(1) @Max(100) Integer maxFileSizeMb
) {}
