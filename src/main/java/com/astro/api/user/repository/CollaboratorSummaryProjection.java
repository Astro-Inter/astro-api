package com.astro.api.user.repository;

public interface CollaboratorSummaryProjection {
    Long getId();
    String getName();
    String getEmail();
    Long getRoleId();
    String getRoleName();
    Long getUnitId();
    String getUnitName();
    String getStatus();
}
