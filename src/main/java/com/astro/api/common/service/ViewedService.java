package com.astro.api.common.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ViewedService {
    private final StringRedisTemplate redisTemplate;

    public ViewedService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Set<String> findViewed(Long userId, String resource, Collection<?> itemIds) {
        List<String> ids = itemIds.stream().map(String::valueOf).distinct().toList();
        if (ids.isEmpty()) {
            return Set.of();
        }
        Map<Object, Boolean> values = redisTemplate.opsForSet().isMember(
                key(userId, resource), ids.toArray());
        return ids.stream().filter(id -> Boolean.TRUE.equals(values.get(id))).collect(java.util.stream.Collectors.toSet());
    }

    private String key(Long userId, String resource) {
        return "%s:viewed:%d".formatted(resource, userId);
    }
}
