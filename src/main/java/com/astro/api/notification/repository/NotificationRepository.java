package com.astro.api.notification.repository;

import com.astro.api.notification.model.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findTop3ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    List<Notification> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

}
