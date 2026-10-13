package com.astro.api.user.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.WorkModel;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.br.CPF;

import java.util.Locale;

public record UpdateCollaboratorRequest(
        @JsonProperty("nome")
        @NotBlank(message = "{collaborator.name.notBlank}")
        @Size(min = 2, max = 255, message = "{collaborator.name.size}") String name,
        @NotBlank(message = "{collaborator.cpf.notBlank}")
        @Pattern(regexp = "[0-9]{11}", message = "{collaborator.cpf.invalid}")
        @CPF(message = "{collaborator.cpf.invalid}") String cpf,
        @NotBlank(message = "{collaborator.email.notBlank}")
        @Email(message = "{collaborator.email.invalid}")
        @Size(max = 255, message = "{collaborator.email.size}") String email,
        @NotNull(message = "{collaborator.roleId.notNull}")
        @Positive(message = "{collaborator.roleId.positive}") Long roleId,
        @NotNull(message = "{collaborator.unitId.notNull}")
        @Positive(message = "{collaborator.unitId.positive}") Long unitId,
        @JsonProperty("modalidade")
        @NotNull(message = "{collaborator.workModel.notNull}") WorkModel workModel,
        @NotNull(message = "{collaborator.status.notNull}") UserStatus status) {
    public UpdateCollaboratorRequest {
        name = name == null ? null : name.trim();
        cpf = cpf == null ? null : cpf.trim().replace(".", "").replace("-", "");
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
