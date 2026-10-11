package com.astro.api.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateEventRequest(
        @NotBlank(message = "{event.title.notBlank}") String title,
        String description,
        String externalLink,
        @NotEmpty(message = "{event.classes.notEmpty}") List<@Valid UpdateEventClassRequest> eventClasses
) {
}
