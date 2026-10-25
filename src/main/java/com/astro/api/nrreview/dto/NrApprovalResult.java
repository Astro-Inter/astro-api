package com.astro.api.nrreview.dto;

import java.util.Map;

public record NrApprovalResult(
        Map<String, Object> document,
        String revision,
        String approvedRevision,
        boolean sqlSynchronized
) {}
