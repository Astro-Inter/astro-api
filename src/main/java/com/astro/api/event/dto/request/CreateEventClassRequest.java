package com.astro.api.event.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record CreateEventClassRequest(
        @Schema(example = "Grupo A")
        @NotBlank(message = "{event.class.name.notBlank}")
        @Size(max = 255, message = "{event.class.name.size}") String name,
        @Schema(example = "2026-11-05T08:00:00")
        @NotNull(message = "{event.class.startDate.notNull}") LocalDateTime startDate,
        @Schema(example = "2026-11-05T12:00:00")
        @NotNull(message = "{event.class.endDate.notNull}") LocalDateTime endDate,
        @NotEmpty(message = "{event.class.participants.notEmpty}")
        List<@NotNull(message = "{event.class.participantId.notNull}")
                @Positive(message = "{event.class.participantId.positive}") Long> participantIds
) {
    public CreateEventClassRequest {
        name = name == null ? null : name.trim();
    }
}
