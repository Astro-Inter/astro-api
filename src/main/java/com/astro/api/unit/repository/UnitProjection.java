package com.astro.api.unit.repository;

public interface UnitProjection {
    Long getUnitId();
    Long getWorkspaceId();
    String getName();
    String getStatus();
    Long getCollaboratorCount();
    String getPostalCode();
    String getStreet();
    String getCity();
    String getNeighborhood();
    String getState();
    String getAddressLine2();
    String getNumber();
}
