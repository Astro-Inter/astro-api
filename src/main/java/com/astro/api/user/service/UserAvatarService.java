package com.astro.api.user.service;

import com.astro.api.common.storage.R2StorageService;
import com.astro.api.common.storage.R2StorageService.PresignedDownload;
import com.astro.api.user.repository.UserProfilePhotoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.SetCondition;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class UserAvatarService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserAvatarService.class);
    private final UserProfilePhotoRepository photos;
    private final StringRedisTemplate redis;
    private final R2StorageService storage;
    private final Clock clock;

    @Autowired
    public UserAvatarService(UserProfilePhotoRepository photos, StringRedisTemplate redis, R2StorageService storage) {
        this(photos, redis, storage, Clock.systemUTC());
    }

    UserAvatarService(UserProfilePhotoRepository photos, StringRedisTemplate redis, R2StorageService storage, Clock clock) {
        this.photos = photos;
        this.redis = redis;
        this.storage = storage;
        this.clock = clock;
    }

    public String findUrl(Long userId) {
        return userId == null ? null : findUrls(List.of(userId)).get(userId);
    }

    // Callers must authorize these IDs before consulting the shared avatar cache.
    public Map<Long, String> findUrls(Collection<Long> userIds) {
        List<Long> ids = userIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> urls = new HashMap<>();
        try {
            List<String> cached = redis.opsForValue().multiGet(ids.stream().map(this::key).toList());
            if (cached != null) {
                for (int index = 0; index < ids.size(); index++) {
                    String url = cached.get(index);
                    if (url != null && !url.isBlank()) {
                        urls.put(ids.get(index), url);
                    }
                }
            }
        } catch (DataAccessException exception) {
            LOGGER.warn("Cache de avatares indisponível para leitura; consultando fotos no banco");
        }
        List<Long> missing = ids.stream().filter(id -> !urls.containsKey(id)).toList();
        if (missing.isEmpty()) {
            return urls;
        }
        Map<Long, PresignedDownload> generated = new LinkedHashMap<>();
        // One database query for all cache misses, including users without a photo.
        for (var photo : photos.findAllById(missing)) {
            PresignedDownload download = storage.getPresignedDownload(photo.getObjectPath());
            if (download != null && download.expiresAt().isAfter(clock.instant())) {
                urls.put(photo.getUserId(), download.url());
                generated.put(photo.getUserId(), download);
            }
        }
        cache(generated);
        return urls;
    }

    private void cache(Map<Long, PresignedDownload> generated) {
        if (generated.isEmpty()) {
            return;
        }
        try {
            redis.executePipelined((RedisCallback<Object>) connection -> {
                generated.forEach((id, download) -> {
                    long remainingMillis = Duration.between(clock.instant(), download.expiresAt()).toMillis();
                    if (remainingMillis > 0) {
                        connection.stringCommands().set(key(id).getBytes(StandardCharsets.UTF_8),
                                download.url().getBytes(StandardCharsets.UTF_8), SetCondition.upsert(),
                                Expiration.unixTimestamp(download.expiresAt().toEpochMilli(), TimeUnit.MILLISECONDS));
                    }
                });
                return null;
            });
        } catch (DataAccessException exception) {
            LOGGER.warn("Cache de avatares indisponível para gravação; retornando URLs recém-geradas");
        }
    }

    public void invalidate(Long userId) {
        redis.delete(key(userId));
    }

    private String key(Long userId) {
        return "user:avatar:" + userId;
    }
}
