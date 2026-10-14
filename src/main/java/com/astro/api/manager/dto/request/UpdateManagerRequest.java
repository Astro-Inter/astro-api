package com.astro.api.manager.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.WorkModel;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.br.CPF;
import java.util.Locale;

public record UpdateManagerRequest(
        @JsonProperty("nome") @NotBlank(message = "{manager.name.required}")
        @Size(min = 2, max = 255, message = "{manager.name.size}") String name,
        @NotBlank(message = "{manager.cpf.required}") @CPF(message = "{manager.cpf.invalid}")
        @Pattern(regexp = "[0-9]{11}", message = "{manager.cpf.invalid}") String cpf,
        @NotBlank(message = "{manager.email.required}") @Email(message = "{manager.email.invalid}")
        @Size(max = 255, message = "{manager.email.size}") String email,
        @NotNull(message = "{manager.roleId.required}") @Positive(message = "{manager.roleId.positive}") Long roleId,
        @NotNull(message = "{manager.unitId.required}") @Positive(message = "{manager.unitId.positive}") Long unitId,
        @JsonProperty("modalidade") @NotNull(message = "{manager.workModel.required}") WorkModel workModel,
        @NotNull(message = "{manager.status.required}") UserStatus status) {
    public UpdateManagerRequest {
        name = name == null ? null : name.trim();
        cpf = cpf == null ? null : cpf.trim().replace(".", "").replace("-", "");
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
