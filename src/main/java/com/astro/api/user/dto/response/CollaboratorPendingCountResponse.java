package com.astro.api.user.dto.response;

public record CollaboratorPendingCountResponse(
        Long id,
        String name,
        String profilePhotoUrl,
        String roleName,
        Long pendingCount) {
}
