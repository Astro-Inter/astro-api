package com.astro.api.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelEventRequest(
        @NotBlank(message = "{event.cancellation.reason.notBlank}")
        @Size(max = 1000, message = "{event.cancellation.reason.size}") String reason
) {
}
