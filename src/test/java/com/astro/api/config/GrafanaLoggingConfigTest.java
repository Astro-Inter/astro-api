package com.astro.api.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.logging.OpenTelemetryLoggingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.logging.otlp.OtlpLoggingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GrafanaLoggingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GrafanaLoggingConfig.class)
            .withConfiguration(AutoConfigurations.of(OpenTelemetrySdkAutoConfiguration.class,
                    OpenTelemetryLoggingAutoConfiguration.class, OtlpLoggingAutoConfiguration.class))
            .withPropertyValues("management.logging.export.otlp.enabled=false",
                    "management.opentelemetry.resource-attributes.service.name=astro-api");

    @Test
    void naoHabilitaExportacaoSemAsDuasVariaveis() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(GrafanaLoggingConfig.class));

        contextRunner.withPropertyValues("OTEL_EXPORTER_OTLP_ENDPOINT=https://example.com/otlp")
                .run(context -> assertThat(context).doesNotHaveBean(GrafanaLoggingConfig.class));

        contextRunner.withPropertyValues("OTEL_EXPORTER_OTLP_HEADERS=Authorization=Basic%20teste")
                .run(context -> assertThat(context).doesNotHaveBean(GrafanaLoggingConfig.class));
    }

    @Test
    void enviaLogPorHttpComAutenticacaoEServico() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
                var executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(10000);
            var request = executor.submit(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(10000);
                    var input = new BufferedInputStream(socket.getInputStream());
                    var headerBytes = new ByteArrayOutputStream();
                    int previous = 0;
                    int current;
                    while ((current = input.read()) != -1) {
                        headerBytes.write(current);
                        previous = (previous << 8) | current;
                        if (previous == 0x0d0a0d0a) {
                            break;
                        }
                    }
                    String headers = headerBytes.toString(StandardCharsets.UTF_8);
                    int contentLength = headers.lines()
                            .filter(line -> line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))
                            .mapToInt(line -> Integer.parseInt(line.substring(line.indexOf(':') + 1).trim()))
                            .findFirst().orElseThrow();
                    String body = new String(input.readNBytes(contentLength), StandardCharsets.UTF_8);
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n"
                            + "Content-Type: application/x-protobuf\r\nConnection: close\r\n\r\n")
                            .getBytes(StandardCharsets.UTF_8));
                    return headers + body;
                }
            });
            contextRunner.withPropertyValues(
                    "OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:" + server.getLocalPort() + "/otlp",
                    "OTEL_EXPORTER_OTLP_HEADERS=Authorization=Basic%20teste")
                    .run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(SdkLoggerProvider.class);
                        LoggerContext loggerContext = new LoggerContext();
                        loggerContext.setMDCAdapter(new ch.qos.logback.classic.util.LogbackMDCAdapter());
                        try {
                            var appender = new OpenTelemetryAppender();
                            appender.setContext(loggerContext);
                            appender.setOpenTelemetry(context.getBean(OpenTelemetry.class));
                            appender.start();
                            Logger logger = loggerContext.getLogger("grafana-test");
                            logger.addAppender(appender);
                            logger.info("Conexao OTLP validada localmente");
                            context.getBean(SdkLoggerProvider.class).forceFlush().join(5, TimeUnit.SECONDS);
                            assertThat(request.get(5, TimeUnit.SECONDS))
                                    .contains("POST /otlp/v1/logs", "Basic teste", "astro-api", "INFO",
                                            "Conexao OTLP validada localmente");
                            appender.stop();
                        }
                        finally {
                            loggerContext.stop();
                        }
                    });
        }
    }
}
