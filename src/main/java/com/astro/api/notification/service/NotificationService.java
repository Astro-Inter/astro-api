package com.astro.api.notification.service;

import com.astro.api.notification.model.Notification;
import com.astro.api.notification.repository.NotificationRepository;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.user.repository.UserRepository;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    public List<Notification> findLatestForUser(Long userId) {
        return notificationRepository.findTop3ByUserIdOrderByCreatedAtDescIdDesc(userId);
    }

    public AuthenticatedNotifications findAllForAuthenticatedUser(String firebaseUid) {
        Long userId = findAuthenticatedUserId(firebaseUid);
        return new AuthenticatedNotifications(userId,
                notificationRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId));
    }

    public Long requireNotificationOwner(String firebaseUid, String notificationId) {
        Long userId = findAuthenticatedUserId(firebaseUid);
        if (!ObjectId.isValid(notificationId)) {
            throw new ResourceNotFoundException("Notificação não encontrada");
        }
        notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificação não encontrada"));
        return userId;
    }

    private Long findAuthenticatedUserId(String firebaseUid) {
        return userRepository.findByFirebaseUid(firebaseUid)
                .map(user -> user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário autenticado não encontrado"));
    }

    public long elapsedWholeHours(Instant createdAt) {
        return elapsedWholeHours(createdAt, Instant.now());
    }

    static long elapsedWholeHours(Instant createdAt, Instant now) {
        return Math.max(0, Duration.between(createdAt, now).toHours());
    }
}
