package com.astro.api.cargo.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CargoNrRequest(
        @NotNull(message = "{cargo.nr.code.notNull}")
        @Positive(message = "{cargo.nr.code.positive}") Integer nr,
        @NotNull(message = "{cargo.nr.active.notNull}") Boolean ativo
) {
}
