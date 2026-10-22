package com.astro.api.unit.dto.response;

public record UnitResponse(
        Long unitId,
        Long workspaceId,
        String name,
        String status,
        long collaboratorCount,
        String postalCode,
        String street,
        String city,
        String neighborhood,
        String state,
        String addressLine2,
        String number
) {
    public UnitResponse(Long unitId, Long workspaceId, String name, String status, long collaboratorCount,
                        String postalCode, String street, String city, String neighborhood,
                        String state, String addressLine2) {
        this(unitId, workspaceId, name, status, collaboratorCount, postalCode, street, city,
                neighborhood, state, addressLine2, null);
    }
}
