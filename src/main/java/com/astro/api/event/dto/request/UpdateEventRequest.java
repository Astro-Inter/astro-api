package com.astro.api.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.CodePointLength;

import java.util.List;

public record UpdateEventRequest(
        @io.swagger.v3.oas.annotations.media.Schema(example = "Treinamento NR 10")
        @NotBlank(message = "{event.title.notBlank}")
        @Size(min = 3, max = 255, message = "{event.title.size}")
        @CodePointLength(min = 3, message = "{event.title.size}") String title,
        @io.swagger.v3.oas.annotations.media.Schema(example = "Orientações do treinamento")
        @NotBlank(message = "{event.description.notBlank}")
        @Size(min = 3, message = "{event.description.size}")
        @Pattern(regexp = "(?s)^ *[^ ].+[^ ] *$", message = "{event.description.size}") String description,
        @Size(max = 2048, message = "{event.externalLink.size}")
        @Pattern(regexp = "(?i)^https?://\\S+$", message = "{event.externalLink.invalid}") String externalLink,
        @NotEmpty(message = "{event.classes.notEmpty}") @io.swagger.v3.oas.annotations.media.Schema(description = "Conjunto desejado de turmas; omissão remove somente futuras. Turmas iniciadas devem permanecer inalteradas.")
        List<@NotNull(message = "{event.class.notNull}") @Valid UpdateEventClassRequest> eventClasses
) {
    public UpdateEventRequest {
        title = title == null ? null : title.trim();
        externalLink = externalLink == null || externalLink.isBlank() ? null : externalLink.trim();
    }
}
