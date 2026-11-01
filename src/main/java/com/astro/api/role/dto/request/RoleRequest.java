package com.astro.api.role.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RoleRequest(
        @NotBlank(message = "{role.firebaseUid.notBlank}") String firebaseUid,
        @NotBlank(message = "{role.name.notBlank}") String name,
        @NotBlank(message = "{role.status.notBlank}")
        @Pattern(regexp = "(?i)\\s*(ativo|inativo)\\s*", message = "{role.status.invalid}") String status
) {
}
