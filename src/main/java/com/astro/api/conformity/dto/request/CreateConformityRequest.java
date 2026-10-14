package com.astro.api.conformity.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record CreateConformityRequest(
        @NotNull(message = "{conformity.collaboratorId.notNull}")
        @Positive(message = "{conformity.collaboratorId.positive}") Long collaboratorId,
        @NotNull(message = "{conformity.nrId.notNull}")
        @Positive(message = "{conformity.nrId.positive}") Integer nrId,
        LocalDate expiresAt) { }
