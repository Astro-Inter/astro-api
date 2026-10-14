package com.astro.api.event.dto.request;

import com.astro.api.event.model.CompletionMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.CodePointLength;

import java.util.List;

public record CreateEventRequest(
        @io.swagger.v3.oas.annotations.media.Schema(example = "Treinamento NR 10")
        @NotBlank(message = "{event.title.notBlank}")
        @Size(min = 3, max = 255, message = "{event.title.size}")
        @CodePointLength(min = 3, message = "{event.title.size}") String title,
        @io.swagger.v3.oas.annotations.media.Schema(example = "Orientações do treinamento")
        @NotBlank(message = "{event.description.notBlank}")
        @Size(min = 3, message = "{event.description.size}")
        @CodePointLength(min = 3, message = "{event.description.size}") String description,
        @Positive(message = "{event.nrId.positive}") Integer nrId,
        @Size(max = 2048, message = "{event.externalLink.size}")
        @Pattern(regexp = "(?i)^https?://\\S+$", message = "{event.externalLink.invalid}") String externalLink,
        @NotNull(message = "{event.completionMode.notNull}") CompletionMode completionMode,
        boolean mandatoryEvidence,
        @NotEmpty(message = "{event.classes.notEmpty}") List<@NotNull(message = "{event.class.notNull}") @Valid CreateEventClassRequest> eventClasses
) {
    public CreateEventRequest {
        title = title == null ? null : title.trim();
        description = description == null ? null : description.trim();
        externalLink = externalLink == null || externalLink.isBlank() ? null : externalLink.trim();
    }
}
