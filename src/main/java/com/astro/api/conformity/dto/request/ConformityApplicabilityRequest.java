package com.astro.api.conformity.dto.request;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonDeserialize(using = ConformityApplicabilityRequestDeserializer.class)
public record ConformityApplicabilityRequest(
        @NotNull(message = "{conformity.applicable.notNull}")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "boolean") Boolean applicable) { }
