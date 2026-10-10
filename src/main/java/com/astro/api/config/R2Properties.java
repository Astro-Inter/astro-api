package com.astro.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "storage.r2")
public record R2Properties(
        String endpoint,
        String bucket,
        String accessKeyId,
        String secretAccessKey,
        Duration presignedUrlDuration) {

    public void validate() {
        if (endpoint == null || endpoint.isBlank() || bucket == null || bucket.isBlank()
                || accessKeyId == null || accessKeyId.isBlank()
                || secretAccessKey == null || secretAccessKey.isBlank()) {
            throw new IllegalStateException("Configure R2_ENDPOINT, R2_BUCKET (ou R2_BUCKET_AVATARS), R2_ACCESS_KEY_ID e R2_SECRET_ACCESS_KEY para acessar fotos de perfil.");
        }
        URI uri = URI.create(endpoint);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("R2_ENDPOINT deve ser uma URL HTTPS do endpoint S3 do R2.");
        }
        if (presignedUrlDuration == null || presignedUrlDuration.compareTo(Duration.ofSeconds(1)) < 0
                || presignedUrlDuration.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalStateException("R2_PRESIGNED_URL_DURATION deve estar entre 1s e 7d.");
        }
    }
}
