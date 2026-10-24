package com.astro.api.form.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FormQuestionAnswerRequest(
        @NotNull(message = "{form.answer.question.required}") @Positive(message = "{form.question.id.positive}") Long questionId,
        @Size(max = 10000, message = "{form.answer.text.size}") String text,
        @Size(max = 100, message = "{form.answer.options.size}") List<@NotNull(message = "{form.answer.option.required}") @Positive(message = "{form.option.id.positive}") Long> optionIds,
        @Size(max = 1000, message = "{form.answer.other.size}") String otherText,
        LocalDate date,
        @Size(max = 10, message = "{form.answer.files.size}") List<@NotNull(message = "{form.answer.file.required}") @PositiveOrZero(message = "{form.answer.file.index}") Integer> fileIndexes) {}
