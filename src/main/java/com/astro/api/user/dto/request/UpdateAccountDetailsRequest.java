package com.astro.api.user.dto.request;

import com.astro.api.user.model.WorkModel;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.br.CPF;

import java.util.Locale;

@Schema(description = "Conjunto completo dos seis campos editáveis da própria conta; todos obrigatórios e não nulos",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record UpdateAccountDetailsRequest(
        @JsonProperty("nome") @NotBlank(message = "{account.name.required}")
        @Size(min = 2, max = 255, message = "{account.name.size}") @Schema(example = "Maria Rosa") String name,
        @NotBlank(message = "{account.cpf.required}") @CPF(message = "{account.cpf.invalid}")
        @Pattern(regexp = "[0-9]{11}", message = "{account.cpf.invalid}") @Schema(example = "52998224725") String cpf,
        @NotBlank(message = "{account.email.required}") @Email(message = "{account.email.invalid}")
        @Size(max = 255, message = "{account.email.size}") @Schema(example = "maria@example.com") String email,
        @NotNull(message = "{account.roleId.required}") @Positive(message = "{account.roleId.positive}")
        @Schema(example = "20") Long roleId,
        @NotNull(message = "{account.unitId.required}") @Positive(message = "{account.unitId.positive}")
        @Schema(example = "10") Long unitId,
        @JsonProperty("modalidade") @NotNull(message = "{account.workModel.required}")
        @Schema(example = "PRESENCIAL") WorkModel workModel) {
    public UpdateAccountDetailsRequest {
        name = name == null ? null : name.trim();
        cpf = cpf == null ? null : cpf.trim().replace(".", "").replace("-", "");
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("Campo não permitido na edição da conta: " + fieldName);
    }
}
