package com.astro.api.collaborator.dto.request;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

public record CollaboratorStatusRequest(
        @NotNull(message = "{collaborator.status.notNull}")
        @Schema(description = "Status desejado explicitamente", allowableValues = {"ATIVO", "DESATIVADO"}) Status status) {
    public enum Status { ATIVO, DESATIVADO }
}
