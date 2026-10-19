package com.astro.api.workspace.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateWorkspaceRequest(
        @NotBlank @Size(min = 2, max = 255) String name,
        @NotBlank @Pattern(regexp = "[0-9]{14}", message = "O CNPJ deve conter 14 dígitos") String cnpj) {
    public UpdateWorkspaceRequest {
        name = name == null ? null : name.trim();
        cnpj = cnpj == null ? null : cnpj.trim().replace(".", "").replace("/", "").replace("-", "");
    }
}
