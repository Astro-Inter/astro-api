package com.astro.api.user.dto.response;

import com.astro.api.user.model.UserStatus;

public record CollaboratorSummaryResponse(
        Long id,
        String name,
        String email,
        Long roleId,
        String roleName,
        Long unitId,
        String unitName,
        UserStatus status,
        String profilePhotoUrl,
        String avatarInitials,
        boolean canManage) {
}
