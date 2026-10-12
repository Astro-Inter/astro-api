package com.astro.api.cargo.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CargoRequest(
        @JsonProperty("firebase_uid") @NotBlank(message = "{cargo.firebaseUid.notBlank}") String firebaseUid,
        @NotBlank(message = "{cargo.name.notBlank}") String nome,
        @NotBlank(message = "{cargo.status.notBlank}")
        @Pattern(regexp = "(?i)\\s*(ativo|inativo)\\s*", message = "{cargo.status.invalid}") String status
) {
}
