package com.astro.api.unit.model;

public record UnitCoordinates(double longitude, double latitude) {
    public UnitCoordinates {
        if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                || longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Coordenadas geográficas inválidas");
        }
    }
}
