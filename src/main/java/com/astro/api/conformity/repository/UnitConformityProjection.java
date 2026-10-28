package com.astro.api.conformity.repository;

import java.time.LocalDate;

public interface UnitConformityProjection {
    Long getId();
    Long getUnitId();
    String getUnitName();
    Integer getNrId();
    String getNrTitle();
    LocalDate getExpiresAt();
    String getOrigin();
    Boolean getApplicable();
    String getFormResponseId();
    String getStatus();
}
