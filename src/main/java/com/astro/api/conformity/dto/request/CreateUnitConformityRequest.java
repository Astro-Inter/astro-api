package com.astro.api.conformity.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record CreateUnitConformityRequest(@NotNull @Positive Long unitId,
        @NotNull @Positive Integer nrId, LocalDate expiresAt) {}
