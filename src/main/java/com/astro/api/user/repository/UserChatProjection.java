package com.astro.api.user.repository;

import com.astro.api.user.model.UserType;
import com.astro.api.user.model.UserStatus;

public interface UserChatProjection {
    Long getId();
    String getName();
    UserType getRole();
    Long getWorkspaceId();
    UserStatus getStatus();
    Boolean getUnitActive();
}
