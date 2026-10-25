package com.astro.api.nrreview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record NrApprovalRequest(
        @NotBlank(message = "{nrReview.revision.required}")
        @Pattern(regexp = "[a-f0-9]{64}", message = "{nrReview.revision.invalid}") String revision,
        @NotBlank(message = "{nrReview.approvedRevision.required}")
        @Pattern(regexp = "[a-f0-9]{64}|absent", message = "{nrReview.approvedRevision.invalid}") String approvedRevision,
        @Valid NrContent corrections
) {}
