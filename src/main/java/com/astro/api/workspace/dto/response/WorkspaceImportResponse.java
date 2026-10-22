package com.astro.api.workspace.dto.response;

import com.astro.api.common.model.SpreadsheetImportMessage;
import java.util.List;

public record WorkspaceImportResponse(int created, int updated, int unchanged, int unitsCreated,
                                      int rolesCreated, List<SpreadsheetImportMessage> ignored) { }
