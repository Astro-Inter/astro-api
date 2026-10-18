package com.astro.api.form.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FormOptionRequest(
        @Positive(message = "{form.option.id.positive}") Long id,
        @Size(max = 80, message = "{form.option.text.size}") String text,
        Boolean other
) {}
