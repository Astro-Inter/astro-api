package com.astro.api.unit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UnitRequest(

        @NotBlank(message = "{unit.name.notBlank}")
        @Size(min = 2, message = "{unit.name.invalid}")
        String name,

        @NotBlank(message = "{unit.cep.notBlank}")
        @Pattern(regexp = "\\d{8}", message = "{unit.cep.invalid}")
        String cep,

        @NotBlank(message = "{unit.state.notBlank}")
        @Size(max = 2, message = "{unit.state.invalid}")
        String state,

        @NotBlank(message = "{unit.city.notBlank}")
        @Size(min = 2, message = "{unit.city.invalid}")
        String city,

        @NotBlank(message = "{unit.neighborhood.notBlank}")
        @Size(min = 2, message = "{unit.neighborhood.invalid}")
        String neighborhood,

        @NotBlank(message = "{unit.street.notBlank}")
        @Size(min = 2, message = "{unit.street.invalid}")
        String street,

        @NotBlank(message = "{unit.number.notBlank}")
        @Size(min = 1, message = "{unit.number.invalid}")
        String number,

        String addressLine2

) {
}
