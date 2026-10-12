package com.astro.api.role.repository;

public interface RoleProjection {
    Long getRoleId();
    Long getWorkspaceId();
    String getName();
    long getCollaboratorCount();
    String getStatus();
}
