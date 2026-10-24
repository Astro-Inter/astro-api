package com.astro.api.form.model;

public record FormLocationPolicy(boolean captureRequired, boolean enforceUnitRadius,
                                 Integer radiusMeters, Integer maxReadingAgeSeconds) {}
