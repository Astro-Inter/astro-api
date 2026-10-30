package com.astro.api.chat.push;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import com.astro.api.chat.model.ChatPushOutboxEntry;
import com.astro.api.chat.mapper.ChatPushEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ChatPushDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatPushDispatcher.class);
    private final ChatPushSettings settings;
    private final ChatPushOutbox outbox;
    private final ChatPushEligibility eligibility;
    private final HttpClient client;

    public ChatPushDispatcher(ChatPushSettings settings, ChatPushOutbox outbox, ChatPushEligibility eligibility,
                              @Qualifier("chatPushHttpClient") HttpClient client) {
        this.settings = settings;
        this.outbox = outbox;
        this.eligibility = eligibility;
        this.client = client;
    }

    @Scheduled(fixedDelayString = "${astro.chat.push.poll-ms:1000}", scheduler = "chatPushScheduler")
    public void dispatch() {
        if (!settings.enabled()) return;
        URI destination;
        try {
            destination = URI.create(settings.url());
            if (!"https".equals(destination.getScheme()) || destination.getHost() == null
                    || destination.getUserInfo() != null || destination.getQuery() != null
                    || destination.getFragment() != null || settings.secret().length() < 32 || settings.secret().length() > 256) {
                LOGGER.error("chat_push_configuration_invalid");
                return;
            }
        } catch (IllegalArgumentException invalid) {
            LOGGER.error("chat_push_configuration_invalid");
            return;
        }
        for (int index = 0; index < 10; index++) {
            String lease = UUID.randomUUID().toString();
            ChatPushOutboxEntry entry = outbox.claim(Instant.now(), lease);
            if (entry == null) return;
            String id = entry.id();
            try {
                if (!eligibility.allows(entry.event())) {
                    outbox.complete(id, lease);
                    continue;
                }
                HttpRequest request = HttpRequest.newBuilder(destination).timeout(Duration.ofSeconds(10))
                        .header("Authorization", "Bearer " + settings.secret()).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(ChatPushEventMapper.toJson(entry.event()))).build();
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 202 || response.statusCode() == 200) {
                    outbox.complete(id, lease);
                } else {
                    outbox.retry(id, lease, Instant.now().plusSeconds(delay(entry.attempts())));
                    LOGGER.warn("chat_push_delivery_rejected status={}", response.statusCode());
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                outbox.retry(id, lease, Instant.now().plusSeconds(30));
                return;
            } catch (Exception unavailable) {
                outbox.retry(id, lease, Instant.now().plusSeconds(delay(entry.attempts())));
                LOGGER.warn("chat_push_delivery_unavailable");
            }
        }
    }

    private static long delay(int attempts) {
        return Math.min(300, 1L << Math.min(9, Math.max(0, attempts - 1)));
    }
}
