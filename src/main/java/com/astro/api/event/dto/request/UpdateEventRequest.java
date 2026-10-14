package com.astro.api.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record UpdateEventRequest(
        @NotBlank(message = "{event.title.notBlank}")
        @Size(min = 3, max = 255, message = "{event.title.size}") String title,
        @NotBlank(message = "{event.description.notBlank}")
        @Size(min = 3, message = "{event.description.size}") String description,
        @Size(max = 2048, message = "{event.externalLink.size}")
        @Pattern(regexp = "(?i)^https?://\\S+$", message = "{event.externalLink.invalid}") String externalLink,
        @NotEmpty(message = "{event.classes.notEmpty}") @io.swagger.v3.oas.annotations.media.Schema(description = "Conjunto desejado de turmas; omissão remove somente futuras. Turmas iniciadas devem permanecer inalteradas.")
        List<@NotNull(message = "{event.class.notNull}") @Valid UpdateEventClassRequest> eventClasses
) {
    public UpdateEventRequest {
        title = title == null ? null : title.trim();
        description = description == null ? null : description.trim();
        externalLink = externalLink == null || externalLink.isBlank() ? null : externalLink.trim();
    }
}
