package com.astro.api.cargo.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CargoNrRequest(@NotNull @Positive Integer nr, @NotNull Boolean ativo) {
}
