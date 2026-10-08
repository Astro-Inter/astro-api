package com.astro.api.event.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateParticipantesTurmaRequest(@NotEmpty List<@NotNull Long> participantesIds) {
}
