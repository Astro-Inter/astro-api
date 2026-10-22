package com.astro.api.user.model;

import com.astro.api.common.text.PersonalDataNormalizer;
import jakarta.validation.constraints.*;
import jakarta.validation.groups.Default;
import org.hibernate.validator.constraints.br.CPF;

/** Dados pessoais de uma linha; os vínculos são resolvidos separadamente no workspace. */
public record CollaboratorImportData(
        @NotBlank(message = "{collaborator.name.notBlank}", groups = {Default.class, Registration.class})
        @Size(min = 2, max = 255, message = "{collaborator.name.size}") String name,
        @NotBlank(message = "{collaborator.cpf.notBlank}", groups = {Default.class, Registration.class})
        @Pattern(regexp = "[0-9]{11}", message = "{collaborator.cpf.invalid}",
                groups = {Default.class, Registration.class})
        @CPF(message = "{collaborator.cpf.invalid}", groups = {Default.class, Registration.class}) String cpf,
        @NotBlank(message = "{collaborator.email.notBlank}", groups = {Default.class, Registration.class})
        @Email(message = "{collaborator.email.invalid}")
        @Size(max = 255, message = "{collaborator.email.size}")
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "{collaborator.email.invalid}",
                groups = Registration.class) String email,
        @NotNull(message = "{collaborator.workModel.notNull}") WorkModel workModel) {
    public CollaboratorImportData {
        name = name == null ? null : PersonalDataNormalizer.name(name);
        cpf = cpf == null ? null : PersonalDataNormalizer.cpf(cpf);
        email = email == null ? null : PersonalDataNormalizer.email(email);
    }

    public interface Registration { }
}
