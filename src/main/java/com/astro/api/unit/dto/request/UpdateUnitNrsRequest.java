package com.astro.api.unit.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateUnitNrsRequest(
        @NotNull(message = "{unit.nrs.notNull}")
        List<@NotNull(message = "{unit.nr.item.notNull}") @Valid UnitNrRequest> nrs
) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public UpdateUnitNrsRequest {
    }
}
