package com.astro.api.manager.dto.request;

import jakarta.validation.constraints.NotNull;

public record ManagerStatusRequest(@NotNull(message = "{manager.status.required}") ManagerAccountStatus status) { }
