package com.astro.api.form.dto.internal;

public record FormImage(
        String id,
        String objectKey,
        String name,
        String contentType,
        long sizeBytes
) {}
