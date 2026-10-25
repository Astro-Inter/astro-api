package com.astro.api.form.model;

import org.springframework.data.mongodb.core.mapping.Field;
import java.time.Instant;

public record FormLocationReading(@Field("latitude") Double latitude,
        @Field("longitude") Double longitude, @Field("precisao_metros") Double accuracyMeters,
        @Field("registrado_em") Instant recordedAt) {}
