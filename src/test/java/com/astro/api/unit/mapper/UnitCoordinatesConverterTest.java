package com.astro.api.unit.mapper;

import com.astro.api.unit.model.UnitCoordinates;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnitCoordinatesConverterTest {
    private final UnitCoordinatesConverter converter = new UnitCoordinatesConverter();

    @Test
    void roundTripsCoordinatesWithLongitudeFirstAndIndependentOfLocale() {
        UnitCoordinates location = new UnitCoordinates(-46.63, -23.55);
        assertEquals("(-46.63,-23.55)", converter.convertToDatabaseColumn(location));
        assertEquals(location, converter.convertToEntityAttribute("(-46.63, -23.55)"));
        assertEquals(location, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(location)));
        assertEquals(new UnitCoordinates(0, 0), converter.convertToEntityAttribute("(0,0)"));
    }

    @Test
    void preservesNullForMissingOrClearedCoordinates() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void rejectsMalformedOrInvalidCoordinates() {
        for (String value : new String[]{"", "1,2", "(1)", "(1,2,3)", "(NaN,2)", "(181,0)", "(0,91)"}) {
            assertThrows(IllegalArgumentException.class, () -> converter.convertToEntityAttribute(value));
        }
    }
}
