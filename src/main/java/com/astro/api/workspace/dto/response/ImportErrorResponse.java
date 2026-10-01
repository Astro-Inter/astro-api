package com.astro.api.workspace.dto.response;

public record ImportErrorResponse(int row, String field, String message) { }
