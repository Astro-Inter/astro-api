package com.astro.api.unit.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = UnitNrRequestDeserializer.class)
public record UnitNrRequest(
        @NotNull(message = "{unit.nr.code.notNull}")
        @Positive(message = "{unit.nr.code.positive}") Integer nr,
        @NotNull(message = "{unit.nr.active.notNull}") Boolean active
) {
}
