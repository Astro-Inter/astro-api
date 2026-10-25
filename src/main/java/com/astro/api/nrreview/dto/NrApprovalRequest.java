package com.astro.api.nrreview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record NrApprovalRequest(
        @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String revision,
        @NotBlank @Pattern(regexp = "[a-f0-9]{64}|absent") String approvedRevision,
        @Valid NrContent corrections
) {}
