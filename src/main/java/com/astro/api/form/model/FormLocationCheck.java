package com.astro.api.form.model;

import com.astro.api.form.mapper.FormNumberConverter;
import org.springframework.data.convert.ValueConverter;
import org.springframework.data.mongodb.core.mapping.Field;

public record FormLocationCheck(
        @Field("unidade_id") @ValueConverter(FormNumberConverter.class) Long unitId,
        @Field("latitude_unidade") double unitLatitude,
        @Field("longitude_unidade") double unitLongitude,
        @Field("raio_metros") int radiusMeters,
        @Field("distancia_metros") double distanceMeters,
        @Field("leitura") FormLocationReading reading) {}
