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
    // Getters JavaBean preservam o contrato do web sem anotações de serialização.
    public Long getId_unidade() {
        return unitId;
    }

    public Long getId_workspace() {
        return workspaceId;
    }

    public String getNome() {
        return name;
    }

    public long getQuantidade_colaboradores() {
        return collaboratorCount;
    }

    public UnitResponse(Long unitId, Long workspaceId, String name, String status, long collaboratorCount,
                        String postalCode, String street, String city, String neighborhood,
                        String state, String addressLine2) {
        this(unitId, workspaceId, name, status, collaboratorCount, postalCode, street, city,
                neighborhood, state, addressLine2, null);
    }
}
