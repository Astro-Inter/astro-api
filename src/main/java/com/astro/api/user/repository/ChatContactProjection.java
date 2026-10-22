package com.astro.api.user.repository;

import com.astro.api.user.model.UserType;

public interface ChatContactProjection {
    Long getId();
    String getName();
    UserType getRole();
}
