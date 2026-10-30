package com.astro.api.role.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateRoleNrsRequest(
        @NotBlank(message = "{role.firebaseUid.notBlank}") String firebaseUid,
        @NotNull(message = "{role.nrs.notNull}")
        List<@NotNull(message = "{role.nr.item.notNull}") @Valid RoleNrRequest> nrs
) {
}
