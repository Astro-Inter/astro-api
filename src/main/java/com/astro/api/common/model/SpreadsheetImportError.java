package com.astro.api.common.model;

public record SpreadsheetImportError(int row, String field, String message) { }
