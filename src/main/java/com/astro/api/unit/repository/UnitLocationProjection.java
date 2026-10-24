package com.astro.api.unit.repository;

public interface UnitLocationProjection {
    Long getUnitId();
    Boolean getActive();
    Double getLatitude();
    Double getLongitude();
}
