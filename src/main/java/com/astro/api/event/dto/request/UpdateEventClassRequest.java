package com.astro.api.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.List;

public record UpdateEventClassRequest(
        @Positive(message = "{event.class.id.positive}") Long id,
        @io.swagger.v3.oas.annotations.media.Schema(example = "Grupo A")
        @NotBlank(message = "{event.class.name.notBlank}")
        @Size(max = 255, message = "{event.class.name.size}") String name,
        @io.swagger.v3.oas.annotations.media.Schema(example = "2026-11-05T08:00:00")
        @NotNull(message = "{event.class.startDate.notNull}") LocalDateTime startDate,
        @io.swagger.v3.oas.annotations.media.Schema(example = "2026-11-05T12:00:00")
        @NotNull(message = "{event.class.endDate.notNull}") LocalDateTime endDate,
        @NotEmpty(message = "{event.class.participants.notEmpty}")
        List<@NotNull(message = "{event.class.participantId.notNull}")
                @Positive(message = "{event.class.participantId.positive}") Long> participantIds
) {
    public UpdateEventClassRequest {
        name = name == null ? null : name.trim();
    }
}
