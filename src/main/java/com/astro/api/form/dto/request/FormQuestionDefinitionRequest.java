package com.astro.api.form.dto.request;

import com.astro.api.form.model.FormQuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record FormQuestionDefinitionRequest(
        @Positive(message = "{form.question.id.positive}") Long id,
        @NotNull(message = "{form.question.type.notNull}") FormQuestionType type,
        @Size(max = 140, message = "{form.question.text.size}") String question,
        Boolean required,
        Boolean multiple,
        @Size(max = 100, message = "{form.question.options.size}")
        List<@NotNull(message = "{form.option.item.notNull}") @Valid FormOptionRequest> options,
        @Min(value = 1, message = "{form.question.maxFiles.range}")
        @Max(value = 10, message = "{form.question.maxFiles.range}") Integer maxFiles,
        @Min(value = 1, message = "{form.question.maxFileSizeMb.range}")
        @Max(value = 100, message = "{form.question.maxFileSizeMb.range}") Integer maxFileSizeMb
) {}
