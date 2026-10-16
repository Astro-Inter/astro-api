package com.astro.api.conformity.repository;

import java.time.LocalDate;

public interface ConformityProjection {
    Long getId();
    Long getCollaboratorId();
    String getCollaboratorName();
    String getCollaboratorEmail();
    Integer getNrId();
    String getNrTitle();
    LocalDate getExpiresAt();
    String getOrigin();
    Boolean getApplicable();
    Long getEventCompletionId();
    String getStatus();
}
