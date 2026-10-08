package com.astro.api.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelEventoRequest(@NotBlank @Size(max = 1000) String motivo) {
}
