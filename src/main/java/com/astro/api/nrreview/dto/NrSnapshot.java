package com.astro.api.nrreview.dto;

import java.util.Map;

public record NrSnapshot(Map<String, Object> document, String revision, String approvedRevision) {}
