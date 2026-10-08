package com.astro.api.event.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateEventoRequest(
        @NotBlank String titulo,
        String descricao,
        String linkExterno
) {
}
