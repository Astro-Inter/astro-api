package com.astro.api.event.dto.request;

import com.astro.api.event.model.CompletionMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateEventRequest(
        @NotBlank(message = "{event.title.notBlank}") String title,
        String description,
        Integer nrId,
        String externalLink,
        @NotNull(message = "{event.completionMode.notNull}") CompletionMode completionMode,
        boolean mandatoryEvidence,
        @NotEmpty(message = "{event.classes.notEmpty}") List<@Valid CreateEventClassRequest> eventClasses
) {
}
