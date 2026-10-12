package com.astro.api.cargo.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CargoStatusRequest(
        @JsonProperty("firebase_uid") @NotBlank(message = "{cargo.firebaseUid.notBlank}") String firebaseUid
) {
}
