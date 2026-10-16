package com.astro.api.notification.service;

import com.astro.api.notification.model.Notification;

import java.util.List;

public record AuthenticatedNotifications(Long userId, List<Notification> notifications) {
}
