package com.astro.api.form.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FormOptionRequest(
        @JsonProperty("id_opcao") @Positive Long id,
        @JsonProperty("texto") @Size(max = 80) String text,
        @JsonProperty("outra") Boolean other
) {}
