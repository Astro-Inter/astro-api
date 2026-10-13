package com.astro.api.common.viewed;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

@Service
public class ViewedService {
    private final StringRedisTemplate redisTemplate;

    public ViewedService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Set<String> findViewed(Long userId, String resource, Collection<?> itemIds) {
        var ids = itemIds.stream().map(String::valueOf).distinct().toList();
        if (ids.isEmpty()) {
            return Set.of();
        }

        List<Boolean> memberships = redisTemplate.execute((RedisCallback<List<Boolean>>) connection ->
                connection.setCommands().sMIsMember(
                        key(userId, resource).getBytes(StandardCharsets.UTF_8),
                        ids.stream().map(id -> id.getBytes(StandardCharsets.UTF_8)).toArray(byte[][]::new)));

        return IntStream.range(0, ids.size())
                .filter(index -> Boolean.TRUE.equals(memberships.get(index)))
                .mapToObj(ids::get)
                .collect(Collectors.toUnmodifiableSet());
    }

    private String key(Long userId, String resource) {
        return resource + ":viewed:" + userId;
    }
}
