package com.astro.api.workspace.dto.request;

import com.astro.api.unit.dto.request.UnitRequest;
import jakarta.validation.constraints.*;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateWorkspaceRequest(

        @NotBlank(message = "{workspace.password.notBlank}")
        String password,

        @NotBlank(message = "{workspace.email.notBlank}")
        @Email(message = "{workspace.email.invalid}")
        String email,

        @NotNull(message = "{workspace.info.notNull}")
        @Valid
        WorkspaceRequest workspace,

        @NotNull(message = "{workspace.unit.notNull}")
        @NotEmpty(message = "{workspace.unit.notEmpty}")
        List<@Valid UnitRequest> units
) {
}
