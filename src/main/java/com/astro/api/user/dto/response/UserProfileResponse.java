package com.astro.api.user.dto.response;

import java.time.LocalDate;
import java.util.List;

public record UserProfileResponse(
        String name,
        String role,
        String unit,
        String workModel,
        String email,
        String profilePhotoUrl,
        List<NrProfileResponse> nrs
) {
    public record NrProfileResponse(
            int code,
            LocalDate expiresAt,
            String description,
            String objective,
            String applicable
    ) {
    }
}
