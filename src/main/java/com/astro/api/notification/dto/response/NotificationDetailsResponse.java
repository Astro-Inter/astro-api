package com.astro.api.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record NotificationDetailsResponse(
        String message,
        String title,
        Long eventId
) {
}
