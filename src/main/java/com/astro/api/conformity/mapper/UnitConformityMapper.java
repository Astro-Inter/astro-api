package com.astro.api.conformity.mapper;

import com.astro.api.conformity.dto.request.CreateUnitConformityRequest;
import com.astro.api.conformity.dto.response.UnitConformityResponse;
import com.astro.api.conformity.model.Conformity;
import com.astro.api.conformity.repository.UnitConformityProjection;
import com.astro.api.conformity.dto.request.UpdateConformityRequest;
import com.astro.api.form.model.Form;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class UnitConformityMapper {
    public Conformity toManual(CreateUnitConformityRequest request) {
        Conformity value = new Conformity();
        value.setType("UNIDADE");
        value.setUnitId(request.unitId());
        value.setNrId(request.nrId());
        value.setExpiresAt(request.expiresAt());
        value.setOrigin("MANUAL");
        value.setApplicable(true);
        return value;
    }
    public Conformity toApprovedForm(Form form, Long unitId, LocalDate expiresAt) {
        Conformity conformity = new Conformity();
        conformity.setType("UNIDADE");
        conformity.setUnitId(unitId);
        conformity.setNrId(form.getNrCode());
        conformity.setApplicable(true);
        conformity.setOrigin("FORMULARIO");
        conformity.setFormResponseId(form.getId());
        conformity.setExpiresAt(expiresAt);
        return conformity;
    }

    public void applyUpdate(Conformity conformity, UpdateConformityRequest request) {
        conformity.setNrId(request.nrId());
        conformity.setExpiresAt(request.expiresAt());
    }

    public UnitConformityResponse toResponse(UnitConformityProjection row) {
        return new UnitConformityResponse(row.getId(), "UNIDADE", row.getUnitId(), row.getUnitName(),
                row.getNrId(), row.getNrTitle(), row.getExpiresAt(), row.getOrigin(),
                Boolean.TRUE.equals(row.getApplicable()), row.getFormResponseId(), row.getStatus());
    }
}
