package com.astro.api.form.dto.response;

import com.astro.api.form.model.FormStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public record FormSummaryResponse(
        String id,
        @JsonProperty("titulo") String title,
        @JsonProperty("descricao") String description,
        @JsonProperty("quantidade_perguntas") int questionCount,
        FormStatus status
) {}
