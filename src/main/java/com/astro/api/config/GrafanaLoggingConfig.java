package com.astro.api.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration(proxyBeanMethods = false)
@Conditional(GrafanaLoggingConfig.CredentialsPresent.class)
public class GrafanaLoggingConfig {

    @Bean(destroyMethod = "close")
    SdkLoggerProvider grafanaLoggerProvider(Environment environment, Resource resource) {
        String endpoint = environment.getRequiredProperty("OTEL_EXPORTER_OTLP_ENDPOINT").trim();
        String headers = environment.getRequiredProperty("OTEL_EXPORTER_OTLP_HEADERS").trim();

        var exporter = OtlpHttpLogRecordExporter.builder()
                .setEndpoint(logsEndpoint(endpoint));
        for (String entry : headers.split(",")) {
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                throw new IllegalArgumentException("OTEL_EXPORTER_OTLP_HEADERS inválido");
            }
            String name = entry.substring(0, separator).trim();
            if (name.isEmpty() || name.contains("\r") || name.contains("\n")) {
                throw new IllegalArgumentException("OTEL_EXPORTER_OTLP_HEADERS inválido");
            }
            try {
                String value = URLDecoder.decode(entry.substring(separator + 1).trim(), StandardCharsets.UTF_8);
                if (value.isBlank() || value.contains("\r") || value.contains("\n")) {
                    throw new IllegalArgumentException();
                }
                exporter.addHeader(name, value);
            }
            catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("OTEL_EXPORTER_OTLP_HEADERS inválido");
            }
        }

        return SdkLoggerProvider.builder()
                .setResource(resource)
                .addLogRecordProcessor(BatchLogRecordProcessor.builder(exporter.build()).build())
                .build();
    }

    @Bean
    InitializingBean installGrafanaAppender(OpenTelemetry openTelemetry) {
        return () -> OpenTelemetryAppender.install(openTelemetry);
    }

    private static String logsEndpoint(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        }
        catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("OTEL_EXPORTER_OTLP_ENDPOINT inválido");
        }
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("OTEL_EXPORTER_OTLP_ENDPOINT inválido");
        }
        String base = endpoint.replaceAll("/+$", "");
        return base.endsWith("/v1/logs") ? base : base + "/v1/logs";
    }

    static class CredentialsPresent implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            return hasText(environment.getProperty("OTEL_EXPORTER_OTLP_ENDPOINT"))
                    && hasText(environment.getProperty("OTEL_EXPORTER_OTLP_HEADERS"));
        }

        private boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
