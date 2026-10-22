package com.astro.api.workspace.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateWorkspaceRequest(
        @NotBlank(message = "{workspace.name.notblank}")
        @Size(min = 2, max = 255, message = "{workspace.name.size}") String name,
        @NotBlank(message = "{workspace.cnpj.notBlank}")
        @Pattern(regexp = "[0-9]{14}", message = "{workspace.cnpj.invalid}") String cnpj) {
    public UpdateWorkspaceRequest {
        name = name == null ? null : name.trim();
        cnpj = cnpj == null ? null : cnpj.trim().replace(".", "").replace("/", "").replace("-", "");
    }
}
