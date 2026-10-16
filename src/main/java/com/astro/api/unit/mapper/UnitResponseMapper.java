package com.astro.api.unit.mapper;

import com.astro.api.conformity.model.NrDocument;
import com.astro.api.unit.dto.response.UnitNrOptionResponse;
import com.astro.api.unit.dto.response.UnitNrResponse;
import com.astro.api.unit.dto.response.UnitResponse;
import com.astro.api.unit.repository.UnitNrProjection;
import com.astro.api.unit.repository.UnitProjection;
import org.springframework.stereotype.Component;

@Component
public class UnitResponseMapper {
    public UnitResponse toResponse(UnitProjection unit) {
        return new UnitResponse(unit.getUnitId(), unit.getWorkspaceId(), unit.getName(),
                unit.getStatus(), unit.getCollaboratorCount(), unit.getPostalCode(), unit.getStreet(),
                unit.getCity(), unit.getNeighborhood(), unit.getState(), unit.getAddressLine2());
    }

    public UnitNrResponse toNrResponse(UnitNrProjection nr) {
        return new UnitNrResponse(nr.getNrCode(), nr.getDescription());
    }

    public UnitNrOptionResponse toNrOptionResponse(NrDocument nr, boolean active) {
        return new UnitNrOptionResponse(nr.getId(), nr.getDescription(), active);
    }
}
