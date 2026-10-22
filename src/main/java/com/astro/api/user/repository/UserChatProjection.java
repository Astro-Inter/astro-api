package com.astro.api.user.repository;

import com.astro.api.user.model.UserType;

public interface UserChatProjection {
    Long getId();
    String getName();
    UserType getRole();
    Long getWorkspaceId();
}
