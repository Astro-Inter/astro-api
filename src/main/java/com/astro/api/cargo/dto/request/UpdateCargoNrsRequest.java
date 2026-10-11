package com.astro.api.cargo.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateCargoNrsRequest(
        @JsonProperty("firebase_uid") @NotBlank String firebaseUid,
        @NotNull List<@NotNull @Valid CargoNrRequest> nrs
) {
}
