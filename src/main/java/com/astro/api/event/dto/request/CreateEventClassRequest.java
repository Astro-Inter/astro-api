package com.astro.api.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record CreateEventClassRequest(
        @NotBlank(message = "{event.class.name.notBlank}") String name,
        @NotNull(message = "{event.class.startDate.notNull}") LocalDateTime startDate,
        @NotNull(message = "{event.class.endDate.notNull}") LocalDateTime endDate,
        @NotEmpty(message = "{event.class.participants.notEmpty}")
        List<@NotNull(message = "{event.class.participantId.notNull}") Long> participantIds
) {
}
