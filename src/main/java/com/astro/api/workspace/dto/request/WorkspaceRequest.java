package com.astro.api.workspace.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WorkspaceRequest(

        @NotBlank(message = "{workspace.name.notblank}")
        String name,

        @NotBlank(message = "{workspace.cnpj.notBlank}")
        @Pattern(regexp = "\\d{14}", message = "{workspace.cnpj.invalid}")
        String cnpj

) {}
