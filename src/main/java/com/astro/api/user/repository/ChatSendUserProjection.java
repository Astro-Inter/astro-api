package com.astro.api.user.repository;

import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;

public interface ChatSendUserProjection {
    Long getId();
    UserType getType();
    UserStatus getStatus();
    Boolean getUnitActive();
    Long getWorkspaceId();
    Boolean getAuthenticatedSender();
}
