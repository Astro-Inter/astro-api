package com.astro.api.nrreview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record NrUpdateRequest(
        @NotBlank(message = "{nrReview.revision.required}")
        @Pattern(regexp = "[a-f0-9]{64}", message = "{nrReview.revision.invalid}") String revision,
        @NotNull(message = "{nrReview.content.required}") @Valid NrContent content
) {}
