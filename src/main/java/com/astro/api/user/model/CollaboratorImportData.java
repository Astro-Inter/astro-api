package com.astro.api.user.model;

import com.astro.api.common.text.PersonalDataNormalizer;
import jakarta.validation.constraints.*;
import jakarta.validation.groups.Default;
import org.hibernate.validator.constraints.br.CPF;

/** Dados pessoais de uma linha; os vínculos são resolvidos separadamente no workspace. */
public record CollaboratorImportData(
        @NotBlank(groups = {Default.class, Registration.class}) @Size(min = 2, max = 255) String name,
        @NotBlank(groups = {Default.class, Registration.class})
        @Pattern(regexp = "[0-9]{11}", groups = {Default.class, Registration.class})
        @CPF(groups = {Default.class, Registration.class}) String cpf,
        @NotBlank(groups = {Default.class, Registration.class}) @Email @Size(max = 255)
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", groups = Registration.class) String email,
        @NotNull WorkModel workModel) {
    public CollaboratorImportData {
        name = name == null ? null : PersonalDataNormalizer.name(name);
        cpf = cpf == null ? null : PersonalDataNormalizer.cpf(cpf);
        email = email == null ? null : PersonalDataNormalizer.email(email);
    }

    public interface Registration { }
}
