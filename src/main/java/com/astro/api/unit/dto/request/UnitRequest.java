package com.astro.api.unit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UnitRequest(

        @NotBlank(message = "{unit.name.notBlank}")
        @Size(min = 2, max = 255, message = "{unit.name.invalid}")
        String name,

        @NotBlank(message = "{unit.cep.notBlank}")
        @Pattern(regexp = "\\d{8}", message = "{unit.cep.invalid}")
        String cep,

        @NotBlank(message = "{unit.state.notBlank}")
        @Pattern(regexp = "AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO", message = "{unit.state.invalid}")
        String state,

        @NotBlank(message = "{unit.city.notBlank}")
        @Size(min = 2, max = 255, message = "{unit.city.invalid}")
        String city,

        @NotBlank(message = "{unit.neighborhood.notBlank}")
        @Size(min = 2, max = 255, message = "{unit.neighborhood.invalid}")
        String neighborhood,

        @NotBlank(message = "{unit.street.notBlank}")
        @Size(min = 2, max = 255, message = "{unit.street.invalid}")
        String street,

        @NotBlank(message = "{unit.number.notBlank}")
        @Size(min = 1, max = 30, message = "{unit.number.invalid}")
        String number,

        @Size(max = 255) String addressLine2

) {
    public UnitRequest {
        name = trim(name);
        cep = cep == null ? null : cep.trim().replace("-", "");
        state = state == null ? null : state.trim().toUpperCase(java.util.Locale.ROOT);
        city = trim(city);
        neighborhood = trim(neighborhood);
        street = trim(street);
        number = trim(number);
        addressLine2 = addressLine2 == null || addressLine2.isBlank() ? null : addressLine2.trim();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
