package com.astro.api.form.dto.request;

import com.astro.api.form.model.FormReviewStatus;
import jakarta.validation.constraints.*;

public record FormReviewRequest(@NotNull(message = "{form.review.status.required}") FormReviewStatus status,
        @Size(max = 1000, message = "{form.review.description.size}") String description) {}
