package com.astro.api.workspace.dto.response;

import com.astro.api.common.model.SpreadsheetImportMessage;
import java.util.List;

public record RegisterWorkspaceResponse(Long workspaceId, List<SpreadsheetImportMessage> importErrors) { }
