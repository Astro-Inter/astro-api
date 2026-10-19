package com.astro.api.notification.repository;

import com.astro.api.notification.model.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends MongoRepository<Notification, Object> {
    List<Notification> findTop3ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    List<Notification> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<Notification> findByIdAndUserId(Object id, Long userId);

}
