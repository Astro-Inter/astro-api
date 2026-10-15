package com.astro.api.form.dto.request;

import com.astro.api.form.model.FormQuestionType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FormQuestionRequest(
        @JsonProperty("id_pergunta") @Positive(message = "{form.question.id.positive}") Long id,
        @JsonProperty("tipo") @NotNull(message = "{form.question.type.notNull}") FormQuestionType type,
        @JsonProperty("pergunta") @Size(max = 140, message = "{form.question.text.size}") String question,
        @JsonProperty("obrigatoria") Boolean required,
        @JsonProperty("multipla_escolha") Boolean multiple,
        @JsonProperty("opcoes") @Size(max = 100, message = "{form.question.options.size}")
        List<@NotNull(message = "{form.option.item.notNull}") @Valid FormOptionRequest> options,
        @JsonProperty("maximo_arquivos") @Min(value = 1, message = "{form.question.maxFiles.range}")
        @Max(value = 10, message = "{form.question.maxFiles.range}") Integer maxFiles,
        @JsonProperty("tamanho_maximo_mb") @Min(value = 1, message = "{form.question.maxFileSizeMb.range}")
        @Max(value = 100, message = "{form.question.maxFileSizeMb.range}") Integer maxFileSizeMb
) {}
