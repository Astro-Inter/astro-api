package com.astro.api.workspace.exception;

import com.astro.api.workspace.dto.response.ImportErrorResponse;

import java.util.List;

public class SpreadsheetImportException extends RuntimeException {
    private final List<ImportErrorResponse> importErrors;

    public SpreadsheetImportException(List<ImportErrorResponse> importErrors) {
        super("A planilha contém linhas inválidas");
        this.importErrors = importErrors;
    }

    public List<ImportErrorResponse> getImportErrors() {
        return importErrors;
    }
}
