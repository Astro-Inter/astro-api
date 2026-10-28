package com.astro.api.user.repository;

public interface CollaboratorPendingCountProjection {
    Long getId();
    String getName();
    String getRoleName();
    Long getPendingCount();
}
