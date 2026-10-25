package com.astro.api.nrreview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record NrUpdateRequest(
        @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String revision,
        @NotNull @Valid NrContent content
) {}
