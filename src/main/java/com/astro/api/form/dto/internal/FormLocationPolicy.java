package com.astro.api.form.dto.internal;

public record FormLocationPolicy(
        boolean captureRequired,
        boolean enforceUnitRadius,
        Integer radiusMeters,
        Integer maxReadingAgeSeconds
) {}
