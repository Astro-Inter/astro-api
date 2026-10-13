package com.astro.api.notification.service;

import com.astro.api.notification.model.Notification;
import com.astro.api.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<Notification> findLatestForUser(Long userId) {
        return notificationRepository.findTop3ByUserIdOrderByCreatedAtDescIdDesc(userId);
    }

    public long elapsedWholeHours(Instant createdAt) {
        return elapsedWholeHours(createdAt, Instant.now());
    }

    static long elapsedWholeHours(Instant createdAt, Instant now) {
        return Math.max(0, Duration.between(createdAt, now).toHours());
    }
}
