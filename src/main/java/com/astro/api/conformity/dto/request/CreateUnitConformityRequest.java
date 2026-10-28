package com.astro.api.conformity.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record CreateUnitConformityRequest(
        @NotNull(message = "{conformity.unitId.notNull}")
        @Positive(message = "{conformity.unitId.positive}") Long unitId,
        @NotNull(message = "{conformity.nrId.notNull}")
        @Positive(message = "{conformity.nrId.positive}") Integer nrId,
        LocalDate expiresAt) {}
