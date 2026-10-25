package com.astro.api.form.dto.request;

import jakarta.validation.constraints.*;

public record FormOptionRequest(
        @Positive(message = "{form.option.id.positive}") Long id,
        @Size(max = 80, message = "{form.option.text.size}") String text,
        Boolean other
) {}
