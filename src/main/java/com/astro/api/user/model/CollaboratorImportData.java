package com.astro.api.user.model;

import jakarta.validation.constraints.*;
import java.util.Locale;
import org.hibernate.validator.constraints.br.CPF;

/** Dados pessoais de uma linha; os vínculos são resolvidos separadamente no workspace. */
public record CollaboratorImportData(
        @NotBlank @Size(min = 2, max = 255) String name,
        @NotBlank @Pattern(regexp = "[0-9]{11}") @CPF String cpf,
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull WorkModel workModel) {
    public CollaboratorImportData {
        name = name == null ? null : name.trim();
        cpf = cpf == null ? null : cpf.trim().replace(".", "").replace("-", "");
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
