package com.astro.api.workspace.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WorkspaceManagerTransferRequest(
        @NotNull(message = "{workspace.managerTransfer.newManagerId.required}")
        @Positive(message = "{workspace.managerTransfer.newManagerId.positive}") Long newManagerId) { }
