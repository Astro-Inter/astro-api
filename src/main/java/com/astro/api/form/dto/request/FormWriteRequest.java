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
        @JsonProperty("titulo") @Size(max = 100) String title,
        @JsonProperty("descricao") @Size(max = 240) String description,
        @JsonProperty("id_gestor") @Positive Long managerId,
        @Positive Integer nr,
        @JsonProperty("id_unidade") @Positive Long unitId,
        @JsonProperty("data_limite") LocalDate dueDate,
        FormStatus status,
        @JsonProperty("perguntas") @Size(max = 100) List<@NotNull @Valid FormQuestionRequest> questions
) {}
