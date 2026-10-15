package com.astro.api.form.dto.request;

import com.astro.api.form.model.FormStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FormWriteRequest(
        @JsonProperty("titulo") @Size(max = 100, message = "{form.title.size}") String title,
        @JsonProperty("descricao") @Size(max = 240, message = "{form.description.size}") String description,
        @JsonProperty("id_gestor") @Positive(message = "{form.managerId.positive}") Long managerId,
        @JsonProperty("nr") @Positive(message = "{form.nrCode.positive}") Integer nrCode,
        @JsonProperty("id_unidade") @Positive(message = "{form.unitId.positive}") Long unitId,
        @JsonProperty("data_limite") LocalDate dueDate,
        FormStatus status,
        @JsonProperty("perguntas") @Size(max = 100, message = "{form.questions.size}")
        List<@NotNull(message = "{form.question.item.notNull}") @Valid FormQuestionRequest> questions
) {}
