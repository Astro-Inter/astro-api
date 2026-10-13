package com.astro.api.user.repository;

public interface CollaboratorDetailsProjection extends CollaboratorSummaryProjection {
    String getCpf();
    String getWorkModel();
    Long getWorkspaceId();
}
