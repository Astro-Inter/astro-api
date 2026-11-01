package com.astro.api.manager.dto.response;

import com.astro.api.user.model.UserStatus;

public record ManagerSummaryResponse(
        Long id,
        String name,
        String email,
        Long unitId,
        String unitName,
        UserStatus status,
        String profilePhotoUrl,
        String avatarInitials) { }
