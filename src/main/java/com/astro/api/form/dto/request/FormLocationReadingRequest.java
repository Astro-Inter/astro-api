package com.astro.api.form.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FormLocationReadingRequest(
        @NotNull(message = "{form.location.latitude.required}") @DecimalMin(value = "-90", message = "{form.location.latitude.range}") @DecimalMax(value = "90", message = "{form.location.latitude.range}") Double latitude,
        @NotNull(message = "{form.location.longitude.required}") @DecimalMin(value = "-180", message = "{form.location.longitude.range}") @DecimalMax(value = "180", message = "{form.location.longitude.range}") Double longitude,
        @NotNull(message = "{form.location.accuracy.required}") @DecimalMin(value = "0", message = "{form.location.accuracy.range}") @DecimalMax(value = "10000", message = "{form.location.accuracy.range}") Double accuracyMeters,
        @NotNull(message = "{form.location.time.required}") Instant recordedAt) {}
