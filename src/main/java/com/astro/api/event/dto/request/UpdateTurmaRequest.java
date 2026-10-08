package com.astro.api.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record UpdateTurmaRequest(
        @NotBlank String nome,
        @NotNull LocalDateTime dataInicial,
        @NotNull LocalDateTime dataTermino
) {
}
