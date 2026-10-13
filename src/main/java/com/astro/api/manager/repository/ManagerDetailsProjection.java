package com.astro.api.manager.repository;

public interface ManagerDetailsProjection extends ManagerSummaryProjection {
    String getCpf();
    String getWorkModel();
    Long getRoleId();
    String getRoleName();
    Long getWorkspaceId();
}
