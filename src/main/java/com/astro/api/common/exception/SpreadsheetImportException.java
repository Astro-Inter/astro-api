package com.astro.api.common.exception;

import com.astro.api.common.model.SpreadsheetImportError;
import java.util.List;

public class SpreadsheetImportException extends RuntimeException {
    private final List<SpreadsheetImportError> importErrors;

    public SpreadsheetImportException(List<SpreadsheetImportError> importErrors) {
        super("A planilha contém linhas inválidas");
        this.importErrors = List.copyOf(importErrors);
    }

    public List<SpreadsheetImportError> getImportErrors() {
        return importErrors;
    }
}
