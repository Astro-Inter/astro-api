package com.astro.api.unit.mapper;

import com.astro.api.conformity.model.NrDocument;
import com.astro.api.unit.dto.response.UnitNrOptionResponse;
import com.astro.api.unit.dto.response.UnitNrResponse;
import com.astro.api.unit.dto.response.UnitResponse;
import com.astro.api.unit.model.Unit;
import com.astro.api.unit.model.UnitAddress;
import com.astro.api.unit.repository.UnitNrProjection;
import com.astro.api.unit.repository.UnitProjection;
import org.springframework.stereotype.Component;

@Component
public class UnitResponseMapper {
    public UnitResponse toResponse(Unit unit, long collaboratorCount) {
        UnitAddress address = unit.address;
        return new UnitResponse(unit.id, unit.workspace.id, unit.name, unit.isActive ? "Ativo" : "Inativo",
                collaboratorCount, address == null ? null : address.cep,
                address == null ? null : address.street, address == null ? null : address.city,
                address == null ? null : address.neighborhood, address == null ? null : address.state,
                address == null ? null : address.addressLine2, address == null ? null : address.number);
    }

    public UnitResponse toResponse(UnitProjection unit) {
        return new UnitResponse(unit.getUnitId(), unit.getWorkspaceId(), unit.getName(),
                unit.getStatus(), unit.getCollaboratorCount(), unit.getPostalCode(), unit.getStreet(),
                unit.getCity(), unit.getNeighborhood(), unit.getState(), unit.getAddressLine2(), unit.getNumber());
    }

    public UnitNrResponse toNrResponse(UnitNrProjection nr) {
        return new UnitNrResponse(nr.getNrCode(), nr.getDescription());
    }

    public UnitNrOptionResponse toNrOptionResponse(NrDocument nr, boolean active) {
        return new UnitNrOptionResponse(nr.getId(), nr.getDescription(), active);
    }
}
