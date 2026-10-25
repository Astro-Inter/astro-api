package com.astro.api.form.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record FormResponseRequest(
        @NotNull(message = "{form.answers.required}")
        @Size(max = 100, message = "{form.questions.size}")
        List<@NotNull(message = "{form.answer.required}") @Valid FormQuestionAnswerRequest> answers
) {}
