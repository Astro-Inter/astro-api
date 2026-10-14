package com.astro.api.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.CodePointLength;

public record CancelEventRequest(
        @NotBlank(message = "{event.cancellation.reason.notBlank}")
        @Size(min = 3, max = 1000, message = "{event.cancellation.reason.size}")
        @CodePointLength(min = 3, message = "{event.cancellation.reason.size}") String reason
) {
    public CancelEventRequest {
        reason = reason == null ? null : reason.trim();
    }
}
