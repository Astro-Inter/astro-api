package com.astro.api.form.service;

import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormLocationPolicy;
import com.astro.api.unit.repository.UnitLocationProjection;
import com.astro.api.unit.repository.UnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormLocationService {
    private static final int UNIT_RADIUS_METERS = 100;
    private final UnitRepository unitRepository;

    public FormLocationService(UnitRepository unitRepository) { this.unitRepository = unitRepository; }

    public FormLocationPolicy policy(Form form) {
        return new FormLocationPolicy(true, false, form.getUnitId() == null ? null : UNIT_RADIUS_METERS, null);
    }

    @Transactional(readOnly = true)
    public UnitLocationProjection findUnitLocation(Form form) {
        if (form.getUnitId() == null) return null;
        return unitRepository.findLocationByIdAndWorkspaceId(form.getUnitId(), form.getWorkspaceId())
                .filter(location -> validCoordinates(location.getLatitude(), location.getLongitude()))
                .orElse(null);
    }

    private boolean validCoordinates(Double latitude, Double longitude) {
        return latitude != null && longitude != null && Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }
}
