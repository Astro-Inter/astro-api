package com.astro.api.role.dto.request;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RoleNrRequest(
        @NotNull(message = "{role.nr.code.notNull}")
        @Positive(message = "{role.nr.code.positive}") Integer nr,
        @NotNull(message = "{role.nr.active.notNull}") Boolean active
) {
}
