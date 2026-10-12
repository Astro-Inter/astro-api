package com.astro.api.role.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record RoleStatusRequest(
        @JsonProperty("firebase_uid") @NotBlank(message = "{role.firebaseUid.notBlank}") String firebaseUid
) {
}
