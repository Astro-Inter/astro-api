package com.astro.api.home.dto.response;


public record PendingFormResponse(
        String id,
        String title,
        Long daysLeft,
        boolean viewed
) {
}
