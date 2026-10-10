package com.astro.api.config;

import com.astro.api.common.storage.R2StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.ResourcePropertySource;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class R2ConfigTest {

    @Test
    void shouldUseAvatarsBucketAsFallbackAndPreferExplicitBucket() throws Exception {
        for (boolean explicitBucket : new boolean[]{false, true}) {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                Map<String, Object> environment = new java.util.HashMap<>(Map.of(
                        "R2_ENDPOINT", "https://account.r2.cloudflarestorage.com",
                        "R2_BUCKET_AVATARS", "avatars",
                        "R2_ACCESS_KEY_ID", "test-access-key",
                        "R2_SECRET_ACCESS_KEY", "test-secret-key"));
                if (explicitBucket) {
                    environment.put("R2_BUCKET", "custom-photos");
                }
                context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("r2-environment", environment));
                context.getEnvironment().getPropertySources().addLast(
                        new ResourcePropertySource("classpath:application.properties"));
                context.register(R2Config.class);
                context.refresh();
                R2Properties properties = context.getBean(R2Properties.class);
                properties.validate();
                assertEquals(Duration.ofDays(1), properties.presignedUrlDuration());
                assertEquals(explicitBucket ? "custom-photos" : "avatars", properties.bucket());
            }
        }
    }

    @Test
    void shouldBindConfigurationAndInitializePresignerOnlyForAnExistingPhoto() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("r2-test", Map.of(
                    "storage.r2.endpoint", "https://account.r2.cloudflarestorage.com",
                    "storage.r2.bucket", "photos",
                    "storage.r2.access-key-id", "test-access-key",
                    "storage.r2.secret-access-key", "test-secret-key",
                    "storage.r2.presigned-url-duration", "15m")));
            context.register(R2Config.class, R2StorageService.class);
            context.refresh();

            assertEquals(Duration.ofMinutes(15), context.getBean(R2Properties.class).presignedUrlDuration());
            R2StorageService storage = context.getBean(R2StorageService.class);
            assertNull(storage.getDownloadUrl(null));
            assertFalse(context.getBeanFactory().containsSingleton("r2Presigner"));

            assertTrue(storage.getDownloadUrl("usuarios/20/foto.jpg").contains("X-Amz-Signature="));
            assertTrue(context.getBeanFactory().containsSingleton("r2Presigner"));
        }
    }

    @Test
    void shouldAllowProfilesWithoutPhotosWhenR2IsNotConfigured() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(R2Config.class, R2StorageService.class)) {
            assertNull(context.getBean(R2StorageService.class).getDownloadUrl(null));
            assertFalse(context.getBeanFactory().containsSingleton("r2Presigner"));
        }
    }
}
