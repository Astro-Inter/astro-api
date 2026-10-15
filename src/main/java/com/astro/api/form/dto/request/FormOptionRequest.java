package com.astro.api.form.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FormOptionRequest(
        @JsonProperty("id_opcao") @Positive(message = "{form.option.id.positive}") Long id,
        @JsonProperty("texto") @Size(max = 80, message = "{form.option.text.size}") String text,
        @JsonProperty("outra") Boolean other
) {}
