package com.astro.api.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfilePhotoRequest(
        @NotBlank(message = "O caminho da foto é obrigatório")
        @Size(max = 2048, message = "O caminho da foto deve ter no máximo 2048 caracteres")
        String objectPath
) {
}
