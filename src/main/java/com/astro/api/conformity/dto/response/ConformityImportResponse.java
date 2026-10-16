package com.astro.api.conformity.dto.response;

import java.util.List;

public record ConformityImportResponse(int processed, int inserted, int updated, int rejected,
                                       List<RejectedRow> rejectedRows) {
    public record RejectedRow(int row, String cpf, String nr, String reason) { }
}
