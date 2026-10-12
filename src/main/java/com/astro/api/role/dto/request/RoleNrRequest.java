package com.astro.api.role.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RoleNrRequest(
        @NotNull(message = "{role.nr.code.notNull}")
        @Positive(message = "{role.nr.code.positive}") Integer nr,
        @JsonProperty("ativo") @NotNull(message = "{role.nr.active.notNull}") Boolean active
) {
}
