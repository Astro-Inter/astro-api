package com.astro.api.user.repository;

import java.time.LocalDateTime;

public interface CollaboratorEventProjection {
    Long getCollaboratorId();
    String getCollaboratorName();
    Long getEventId();
    String getEventTitle();
    LocalDateTime getClassLimitDate();
    String getCompletionStatus();
    String getEventStatus();
}
