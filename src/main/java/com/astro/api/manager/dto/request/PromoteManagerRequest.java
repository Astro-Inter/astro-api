package com.astro.api.manager.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PromoteManagerRequest(
        @NotNull(message = "{manager.collaboratorId.required}")
        @Positive(message = "{manager.collaboratorId.positive}") Long collaboratorId,
        @NotNull(message = "{manager.status.required}") ManagerAccountStatus status) { }
