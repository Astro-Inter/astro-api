package com.astro.api.unit.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

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
    // Compatibilidade com a listagem de unidades e o editor de formulários do web.
    @JsonProperty("id_unidade")
    public Long legacyUnitId() {
        return unitId;
    }

    @JsonProperty("id_workspace")
    public Long legacyWorkspaceId() {
        return workspaceId;
    }

    @JsonProperty("nome")
    public String legacyName() {
        return name;
    }

    @JsonProperty("quantidade_colaboradores")
    public long legacyCollaboratorCount() {
        return collaboratorCount;
    }

    public UnitResponse(Long unitId, Long workspaceId, String name, String status, long collaboratorCount,
                        String postalCode, String street, String city, String neighborhood,
                        String state, String addressLine2) {
        this(unitId, workspaceId, name, status, collaboratorCount, postalCode, street, city,
                neighborhood, state, addressLine2, null);
    }
}
