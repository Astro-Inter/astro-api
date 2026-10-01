package com.astro.api.unit.mapper;

import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.model.Unit;
import com.astro.api.unit.model.UnitAddress;
import com.astro.api.workspace.model.Workspace;
import org.springframework.stereotype.Component;

@Component
public class UnitMapper {
    public Unit toUnit(UnitRequest request, Workspace workspace) {
        Unit unit = new Unit();
        unit.name = request.name().trim();
        unit.isActive = true;
        unit.workspace = workspace;
        return unit;
    }

    public UnitAddress toAddress(UnitRequest request, Unit unit) {
        UnitAddress address = new UnitAddress();
        address.cep = request.cep();
        address.street = request.street();
        address.city = request.city();
        address.neighborhood = request.neighborhood();
        address.state = request.state();
        address.addressLine2 = hasText(request.addressLine2()) ? request.addressLine2().trim() : null;
        address.unit = unit;
        return address;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
