package com.astro.api.conformity.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record UpdateConformityRequest(
        @NotNull(message = "{conformity.nrId.notNull}")
        @Positive(message = "{conformity.nrId.positive}") Integer nrId,
        LocalDate expiresAt) { }
