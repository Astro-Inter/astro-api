package com.astro.api.collaborator.repository;

public interface CollaboratorDetailsProjection extends CollaboratorSummaryProjection {
    String getCpf();
    String getWorkModel();
    Long getWorkspaceId();
}
