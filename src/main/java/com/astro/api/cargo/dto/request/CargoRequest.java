package com.astro.api.cargo.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CargoRequest(
        @JsonProperty("firebase_uid") @NotBlank String firebaseUid,
        @NotBlank String nome,
        @NotBlank @Pattern(regexp = "(?i)\\s*(ativo|inativo)\\s*", message = "deve ser Ativo ou Inativo") String status
) {
}
