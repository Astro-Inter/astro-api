package com.astro.api.event.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CompletionRejectionRequest(
        @NotBlank(message = "{event.completion.rejectionReason.notBlank}")
        String rejectionReason
) {
    public CompletionRejectionRequest {
        rejectionReason = rejectionReason == null ? null : rejectionReason.trim();
    }
}
