package com.astro.api.role.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RoleStatusRequest(
        @NotBlank(message = "{role.firebaseUid.notBlank}") String firebaseUid
) {
}
