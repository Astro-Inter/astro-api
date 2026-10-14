package com.astro.api.event.repository;

public interface EventCandidateProjection {
    Long getId();
    String getName();
    String getEmail();
    Long getRoleId();
    String getRoleName();
    Long getUnitId();
    String getUnitName();
    String getWorkModel();
}
