package com.astro.api.form.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record FormResponseRequest(
        @NotNull(message = "{form.location.consent.required}") @AssertTrue(message = "{form.location.consent.required}") Boolean locationConsent,
        @NotEmpty(message = "{form.location.readings.required}") @Size(max = 1440, message = "{form.location.readings.size}") List<@NotNull(message = "{form.location.reading.required}") @Valid FormLocationReadingRequest> locations,
        @NotNull(message = "{form.answers.required}") @Size(max = 100, message = "{form.questions.size}") List<@NotNull(message = "{form.answer.required}") @Valid FormQuestionAnswerRequest> answers) {}
