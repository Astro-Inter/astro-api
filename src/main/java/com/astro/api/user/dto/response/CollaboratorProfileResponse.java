package com.astro.api.user.dto.response;

import com.astro.api.user.model.WorkModel;
import java.util.List;

public record CollaboratorProfileResponse(
        Long id,
        String name,
        String role,
        String profilePhotoUrl,
        String unitName,
        WorkModel workModel,
        String email,
        Long pendingCount,
        List<UserProfileResponse.NrProfileResponse> nrs) {
}
