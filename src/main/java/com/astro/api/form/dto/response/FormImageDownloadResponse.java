package com.astro.api.form.dto.response;

import java.time.Instant;

public record FormImageDownloadResponse(String id, String url, Instant expiresAt) {}
