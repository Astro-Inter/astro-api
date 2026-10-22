package com.astro.api.user.model;

import com.astro.api.common.model.SpreadsheetImportMessage;
import com.astro.api.role.model.Role;
import com.astro.api.unit.model.Unit;
import java.util.List;

public record CollaboratorImportPlan(List<Change> changes, int unchanged, List<SpreadsheetImportMessage> ignored) {
    public record Change(int row, CollaboratorImportData data, User existingCollaborator, Unit unit, Role role) { }
}
