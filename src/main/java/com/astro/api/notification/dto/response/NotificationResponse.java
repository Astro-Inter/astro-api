package com.astro.api.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NotificationResponse(
        String id,
        String title,
        boolean viewed,
        long hours
) {
}
