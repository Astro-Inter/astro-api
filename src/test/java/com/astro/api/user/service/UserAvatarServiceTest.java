package com.astro.api.user.service;

import com.astro.api.common.storage.R2StorageService;
import com.astro.api.common.storage.R2StorageService.PresignedDownload;
import com.astro.api.user.model.UserProfilePhoto;
import com.astro.api.user.repository.UserProfilePhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.SetCondition;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.types.Expiration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalMatchers.aryEq;

class UserAvatarServiceTest {
    private final UserProfilePhotoRepository photos = mock(UserProfilePhotoRepository.class);
    private final R2StorageService storage = mock(R2StorageService.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final RedisConnection connection = mock(RedisConnection.class);
    private final RedisStringCommands commands = mock(RedisStringCommands.class);
    private final Clock clock = mock(Clock.class);
    private final Instant now = Instant.parse("2026-10-13T02:00:00Z");
    private final UserAvatarService service = new UserAvatarService(photos, redis, storage, clock);

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(now);
        when(redis.opsForValue()).thenReturn(values);
        when(values.multiGet(any())).thenAnswer(invocation -> {
            java.util.Collection<?> keys = invocation.getArgument(0);
            return new java.util.ArrayList<>(java.util.Collections.nCopies(keys.size(), null));
        });
        when(connection.stringCommands()).thenReturn(commands);
        when(redis.executePipelined(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<?> callback = invocation.getArgument(0);
            assertNull(callback.doInRedis(connection));
            return List.of();
        });
    }

    @Test
    void reusesCachedUrlsWithoutDatabaseQueriesOrSigningAndDoesNotExtendTheirTtl() {
        when(values.multiGet(List.of("usuario:foto:20", "usuario:foto:21")))
                .thenReturn(List.of("https://r2.example.com/20", "https://r2.example.com/21"));
        assertEquals(Map.of(20L, "https://r2.example.com/20", 21L, "https://r2.example.com/21"),
                service.findUrls(List.of(20L, 21L, 20L)));
        verifyNoInteractions(photos, storage, commands);
        verify(redis, never()).executePipelined(any(RedisCallback.class));
    }

    @Test
    void loadsOnlyMissingPhotosInOneQueryAndCachesOnlyTheirPlainUrlWithItsExactExpiration() {
        doReturn(Arrays.asList("https://r2.example.com/cached", null, null)).when(values).multiGet(any());
        when(photos.findAllById(List.of(21L, 22L))).thenReturn(List.of(photo(21L)));
        Instant expiration = now.plusSeconds(3 * 86400);
        when(storage.getPresignedDownload("usuarios/21/foto.jpg"))
                .thenReturn(new PresignedDownload("https://r2.example.com/new", expiration));

        var result = service.findUrls(List.of(20L, 21L, 22L));

        assertEquals(Map.of(20L, "https://r2.example.com/cached", 21L, "https://r2.example.com/new"), result);
        assertNull(result.get(22L));
        verify(photos, times(1)).findAllById(List.of(21L, 22L));
        ArgumentCaptor<Expiration> expiry = ArgumentCaptor.forClass(Expiration.class);
        verify(commands).set(aryEq(bytes("usuario:foto:21")), aryEq(bytes("https://r2.example.com/new")),
                any(SetCondition.class), expiry.capture());
        assertTrue(expiry.getValue().isUnixTimestamp());
        assertEquals(expiration.toEpochMilli(), expiry.getValue().getExpirationTimeInMilliseconds());
    }

    @Test
    void timeSpentSigningDoesNotExtendRedisPastTheSignedUrlDeadline() {
        Instant deadline = now.plusSeconds(86400);
        when(photos.findAllById(List.of(20L))).thenReturn(List.of(photo(20L)));
        when(storage.getPresignedDownload(anyString())).thenAnswer(invocation -> {
            when(clock.instant()).thenReturn(now.plusSeconds(10));
            return new PresignedDownload("https://r2.example.com/new", deadline);
        });

        assertEquals("https://r2.example.com/new", service.findUrl(20L));

        ArgumentCaptor<Expiration> expiry = ArgumentCaptor.forClass(Expiration.class);
        verify(commands).set(any(), any(), any(SetCondition.class), expiry.capture());
        assertEquals(deadline.toEpochMilli(), expiry.getValue().getExpirationTimeInMilliseconds());
    }

    @Test
    void missingPhotosProduceNullWithoutSigningOrCachingAPlaceholder() {
        when(photos.findAllById(List.of(20L))).thenReturn(List.of());
        assertNull(service.findUrl(20L));
        verifyNoInteractions(storage, commands);
        verify(redis, never()).executePipelined(any(RedisCallback.class));
    }

    @Test
    void regeneratesOnACacheMissAndTheNextRequestReusesTheResult() {
        when(values.multiGet(List.of("usuario:foto:20"))).thenReturn(Arrays.asList((String) null))
                .thenReturn(List.of("https://r2.example.com/new"));
        when(photos.findAllById(List.of(20L))).thenReturn(List.of(photo(20L)));
        when(storage.getPresignedDownload(anyString()))
                .thenReturn(new PresignedDownload("https://r2.example.com/new", now.plusSeconds(60)));
        assertEquals("https://r2.example.com/new", service.findUrl(20L));
        assertEquals("https://r2.example.com/new", service.findUrl(20L));
        verify(photos, times(1)).findAllById(List.of(20L));
        verify(storage, times(1)).getPresignedDownload(anyString());
    }

    @Test
    void redisReadOrWriteFailureStillReturnsTheFreshlyGeneratedPhotoUrl() {
        doThrow(new RedisConnectionFailureException("Redis unavailable")).when(values).multiGet(any());
        doThrow(new RedisConnectionFailureException("Redis unavailable"))
                .when(redis).executePipelined(any(RedisCallback.class));
        when(photos.findAllById(List.of(20L))).thenReturn(List.of(photo(20L)));
        when(storage.getPresignedDownload(anyString()))
                .thenReturn(new PresignedDownload("https://r2.example.com/new", now.plusSeconds(60)));
        assertEquals("https://r2.example.com/new", service.findUrl(20L));
    }

    @Test
    void neverCachesOrReturnsAnAlreadyExpiredSignature() {
        when(photos.findAllById(List.of(20L))).thenReturn(List.of(photo(20L)));
        when(storage.getPresignedDownload(anyString()))
                .thenReturn(new PresignedDownload("https://r2.example.com/expired", now.minusSeconds(1)));
        assertNull(service.findUrl(20L));
        verifyNoInteractions(commands);
    }

    @Test
    void emptyOrMissingIdsDoNotConsultExternalServices() {
        assertEquals(Map.of(), service.findUrls(List.of()));
        assertNull(service.findUrl(null));
        verifyNoInteractions(photos, storage, redis);
    }

    @Test
    void invalidatesOnlyTheUsersAvatarKey() {
        service.invalidate(20L);
        verify(redis).delete("usuario:foto:20");
        verifyNoInteractions(photos, storage);
    }

    private UserProfilePhoto photo(Long id) {
        var photo = new UserProfilePhoto();
        photo.setUserId(id);
        photo.setObjectPath("usuarios/" + id + "/foto.jpg");
        return photo;
    }

    private byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
