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
        @NotBlank(message = "{event.class.name.notBlank}")
        @Size(max = 255, message = "{event.class.name.size}") String name,
        @NotNull(message = "{event.class.startDate.notNull}") LocalDateTime startDate,
        @NotNull(message = "{event.class.endDate.notNull}") LocalDateTime endDate,
        @NotEmpty(message = "{event.class.participants.notEmpty}")
        List<@NotNull(message = "{event.class.participantId.notNull}")
                @Positive(message = "{event.class.participantId.positive}") Long> participantIds
) {
    public UpdateEventClassRequest {
        name = name == null ? null : name.trim();
    }
}
