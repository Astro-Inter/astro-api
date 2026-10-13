package com.astro.api.role.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateRoleNrsRequest(
        @JsonProperty("firebase_uid") @NotBlank(message = "{role.firebaseUid.notBlank}") String firebaseUid,
        @NotNull(message = "{role.nrs.notNull}")
        List<@NotNull(message = "{role.nr.item.notNull}") @Valid RoleNrRequest> nrs
) {
}
