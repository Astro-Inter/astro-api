package com.astro.api.home.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PendingFormResponse(
        String id,
        String title,
        @JsonProperty("days_left") Long daysLeft,
        boolean viewed
) {
}
