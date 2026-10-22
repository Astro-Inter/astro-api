package com.astro.api.common.exception;

import com.astro.api.common.model.SpreadsheetImportMessage;
import java.util.List;

public class SpreadsheetImportException extends RuntimeException {
    private final List<SpreadsheetImportMessage> importErrors;

    public SpreadsheetImportException(List<SpreadsheetImportMessage> importErrors) {
        super("A planilha contém linhas inválidas");
        this.importErrors = List.copyOf(importErrors);
    }

    public List<SpreadsheetImportMessage> getImportErrors() {
        return importErrors;
    }
}
