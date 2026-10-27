package com.astro.api.unit.mapper;

import com.astro.api.unit.model.UnitCoordinates;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class UnitCoordinatesConverter implements AttributeConverter<UnitCoordinates, String> {
    @Override
    public String convertToDatabaseColumn(UnitCoordinates coordinates) {
        return coordinates == null ? null : "(" + coordinates.longitude() + "," + coordinates.latitude() + ")";
    }

    @Override
    public UnitCoordinates convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        if (!value.startsWith("(") || !value.endsWith(")")) {
            throw new IllegalArgumentException("Formato de geolocalização inválido");
        }
        String[] coordinates = value.substring(1, value.length() - 1).split(",", -1);
        if (coordinates.length != 2) {
            throw new IllegalArgumentException("Formato de geolocalização inválido");
        }
        return new UnitCoordinates(Double.parseDouble(coordinates[0].trim()),
                Double.parseDouble(coordinates[1].trim()));
    }
}
