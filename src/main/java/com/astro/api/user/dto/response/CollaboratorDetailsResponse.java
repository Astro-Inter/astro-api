package com.astro.api.user.dto.response;

import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.WorkModel;

public record CollaboratorDetailsResponse(
        Long id,
        String name,
        String cpf,
        String email,
        WorkModel workModel,
        UserStatus status,
        Long roleId,
        String roleName,
        Long unitId,
        String unitName,
        Long workspaceId,
        String profilePhotoUrl,
        String avatarInitials,
        boolean canManage) {
}
