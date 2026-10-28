package com.astro.api.event.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CompletionRejectionRequest(
        @NotBlank(message = "Justificativa da rejeição é obrigatória")
        String rejectionReason
) {
}
