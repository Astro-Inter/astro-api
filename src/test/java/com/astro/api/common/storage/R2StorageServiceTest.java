package com.astro.api.common.storage;

import com.astro.api.config.R2Config;
import com.astro.api.config.R2Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class R2StorageServiceTest {

    @Test
    void shouldSignGetForStoredKeyWithConfiguredBucketAndExpiration() {
        R2Properties properties = properties(Duration.ofDays(1));
        try (S3Presigner realPresigner = new R2Config().r2Presigner(properties)) {
            S3Presigner presigner = mock(S3Presigner.class);
            ObjectProvider<S3Presigner> provider = provider(presigner);
            AtomicReference<GetObjectPresignRequest> captured = new AtomicReference<>();
            when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenAnswer(invocation -> {
                GetObjectPresignRequest request = invocation.getArgument(0);
                captured.set(request);
                var signedRequest = realPresigner.presignGetObject(request);
                assertEquals("GET", signedRequest.httpRequest().method().name());
                assertTrue(signedRequest.isBrowserExecutable());
                return signedRequest;
            });

            var download = new R2StorageService(provider, properties, null)
                    .getPresignedDownload("usuarios/20/foto com acento-é+1.jpg");
            URI url = URI.create(download.url());

            assertEquals("photos", captured.get().getObjectRequest().bucket());
            assertEquals("usuarios/20/foto com acento-é+1.jpg", captured.get().getObjectRequest().key());
            assertEquals(Duration.ofDays(1), captured.get().signatureDuration());
            assertEquals("https", url.getScheme());
            assertEquals("account.r2.cloudflarestorage.com", url.getHost());
            assertEquals("/photos/usuarios/20/foto com acento-é+1.jpg", url.getPath());
            assertTrue(url.getRawPath().contains("%20"));
            assertTrue(url.getQuery().contains("X-Amz-Expires=86400"));
            assertTrue(url.getQuery().contains("X-Amz-Signature="));
            assertTrue(url.getQuery().contains("/auto/s3/aws4_request"));
            String signatureDate = Arrays.stream(url.getQuery().split("&"))
                    .filter(parameter -> parameter.startsWith("X-Amz-Date="))
                    .findFirst().orElseThrow().substring("X-Amz-Date=".length());
            var format = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
            assertEquals(Instant.from(format.parse(signatureDate)).plusSeconds(86400), download.expiresAt());
        }
    }

    @Test
    void shouldNotInitializeR2WhenPhotoIsMissingOrBlank() {
        ObjectProvider<S3Presigner> provider = provider(mock(S3Presigner.class));
        R2StorageService service = new R2StorageService(provider, properties(Duration.ofMinutes(15)), null);
        assertNull(service.getDownloadUrl(null));
        assertNull(service.getDownloadUrl(""));
        assertNull(service.getDownloadUrl("   "));
        verifyNoInteractions(provider);
    }

    @Test
    void shouldRejectMissingConfigurationAndInvalidExpiration() {
        assertThrows(IllegalStateException.class, () -> new R2Config().r2Presigner(
                new R2Properties("", "", "", "", Duration.ofMinutes(15))));
        for (Duration duration : new Duration[]{Duration.ZERO, Duration.ofSeconds(-1), Duration.ofDays(8)}) {
            assertThrows(IllegalStateException.class, () -> new R2Config().r2Presigner(properties(duration)));
        }
    }

    private R2Properties properties(Duration duration) {
        return new R2Properties("https://account.r2.cloudflarestorage.com", "photos",
                "test-access-key", "test-secret-key", duration);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<S3Presigner> provider(S3Presigner presigner) {
        ObjectProvider<S3Presigner> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(presigner);
        return provider;
    }
}
