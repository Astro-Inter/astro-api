package com.astro.api.role.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RoleRequest(
        @JsonProperty("firebase_uid") @NotBlank(message = "{role.firebaseUid.notBlank}") String firebaseUid,
        @JsonProperty("nome") @NotBlank(message = "{role.name.notBlank}") String name,
        @NotBlank(message = "{role.status.notBlank}")
        @Pattern(regexp = "(?i)\\s*(ativo|inativo)\\s*", message = "{role.status.invalid}") String status
) {
}
