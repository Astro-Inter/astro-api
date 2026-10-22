package com.astro.api.notification.dto.response;

public record NotificationResponse(String id, String title, boolean viewed, long hours) {
}
