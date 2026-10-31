package com.astro.api.user.service;

import com.astro.api.common.storage.R2StorageService;
import com.astro.api.common.storage.R2StorageService.PresignedDownload;
import com.astro.api.user.model.UserProfilePhoto;
import com.astro.api.user.repository.UserProfilePhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses only an explicitly configured disposable Redis bound to loopback. */
@EnabledIfEnvironmentVariable(named = "AVATAR_TEST_REDIS_PORT", matches = "[0-9]{4,5}")
class UserAvatarRedisTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private UserAvatarService service;
    private final UserProfilePhotoRepository photos = mock(UserProfilePhotoRepository.class);
    private final R2StorageService storage = mock(R2StorageService.class);
    private final Long id = 951720L;
    private final String key = "usuario:foto:" + id;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getenv("AVATAR_TEST_REDIS_PORT")));
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
        redis.delete(key);
        service = new UserAvatarService(photos, redis, storage);
        var photo = new UserProfilePhoto();
        photo.setUserId(id);
        photo.setObjectPath("usuarios/" + id + "/foto.jpg");
        when(photos.findAllById(List.of(id))).thenReturn(List.of(photo));
    }

    @AfterEach
    void cleanUp() {
        try {
            if (redis != null) { redis.delete(key); }
        } finally {
            if (factory != null) { factory.destroy(); }
        }
    }

    @Test
    void storesAStringWithTheSignedUrlsRemainingTtlReusesItAndInvalidatesIt() {
        Instant expiration = Instant.now().plusSeconds(30);
        when(storage.getPresignedDownload(anyString()))
                .thenReturn(new PresignedDownload("https://r2.example.com/first", expiration))
                .thenReturn(new PresignedDownload("https://r2.example.com/replaced", expiration));

        assertEquals("https://r2.example.com/first", service.findUrl(id));
        assertEquals(DataType.STRING, redis.type(key));
        assertEquals("https://r2.example.com/first", redis.opsForValue().get(key));
        long ttl = redis.getExpire(key, TimeUnit.MILLISECONDS);
        assertTrue(ttl > 0 && ttl <= 30_000, "TTL must be bounded by the URL expiration");
        assertEquals("https://r2.example.com/first", service.findUrl(id));
        verify(photos, times(1)).findAllById(List.of(id));
        verify(storage, times(1)).getPresignedDownload(anyString());

        service.invalidate(id);
        assertFalse(redis.hasKey(key));
        assertEquals("https://r2.example.com/replaced", service.findUrl(id));
        verify(photos, times(2)).findAllById(List.of(id));
    }

    @Test
    void naturallyExpiredKeysCauseANewSignatureInsteadOfReusingTheExpiredUrl() throws InterruptedException {
        when(storage.getPresignedDownload(anyString())).thenAnswer(invocation ->
                new PresignedDownload("https://r2.example.com/" + Instant.now().toEpochMilli(),
                        Instant.now().plusMillis(750)));
        String previous = service.findUrl(id);
        long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (Boolean.TRUE.equals(redis.hasKey(key)) && System.nanoTime() < limit) {
            Thread.sleep(50);
        }
        assertFalse(redis.hasKey(key));
        assertNotEquals(previous, service.findUrl(id));
        verify(storage, times(2)).getPresignedDownload(anyString());
    }
}
