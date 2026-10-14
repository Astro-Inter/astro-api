package com.astro.api.manager.repository;

public interface ManagerSummaryProjection {
    Long getId();
    String getName();
    String getEmail();
    Long getUnitId();
    String getUnitName();
    String getStatus();
}
