package com.astro.api.workspace.dto.request;

import com.astro.api.unit.dto.request.UnitRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record WorkspaceImportRequest(@Size(max = 100) List<@NotNull @Valid UnitRequest> units) {
    public WorkspaceImportRequest {
        units = units == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(units));
    }
}
