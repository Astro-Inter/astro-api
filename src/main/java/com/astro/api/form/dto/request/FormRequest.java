package com.astro.api.form.dto.request;

import com.astro.api.form.model.FormStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FormRequest(
        @Size(max = 100, message = "{form.title.size}") String title,
        @Size(max = 240, message = "{form.description.size}") String description,
        @Positive(message = "{form.managerId.positive}") Long managerId,
        @Positive(message = "{form.nrCode.positive}") Integer nrCode,
        @Positive(message = "{form.unitId.positive}") Long unitId,
        LocalDate dueDate,
        FormStatus status,
        @Size(max = 100, message = "{form.questions.size}")
        List<@NotNull(message = "{form.question.item.notNull}") @Valid FormQuestionDefinitionRequest> questions
) {}
