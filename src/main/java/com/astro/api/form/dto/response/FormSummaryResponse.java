package com.astro.api.form.dto.response;

import com.astro.api.form.model.FormStatus;

public record FormSummaryResponse(
        String id,
        String title,
        String description,
        int questionCount,
        FormStatus status,
        FormAppearanceResponse appearance,
        String reviewStatus
) {}
