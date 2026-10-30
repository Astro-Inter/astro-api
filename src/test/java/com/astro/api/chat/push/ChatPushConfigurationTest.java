package com.astro.api.chat.push;

import com.astro.api.chat.config.ChatPushHttpConfig;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ChatPushConfigurationTest {
    @Test void wiresSingleConstructorWithoutAutowiredAndPreservesTransportSettings() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ChatPushSettings.class, () -> new ChatPushSettings(false, "", ""));
            context.registerBean(ChatPushOutbox.class, () -> mock(ChatPushOutbox.class));
            context.registerBean(ChatPushEligibility.class, () -> mock(ChatPushEligibility.class));
            context.registerBean("otherHttpClient", HttpClient.class, () -> mock(HttpClient.class));
            context.register(ChatPushHttpConfig.class, ChatPushSchedulingConfig.class, ChatPushDispatcher.class);
            context.refresh();
            assertNotNull(context.getBean(ChatPushDispatcher.class));
            HttpClient client = context.getBean("chatPushHttpClient", HttpClient.class);
            assertEquals(Duration.ofSeconds(5), client.connectTimeout().orElseThrow());
            assertEquals(HttpClient.Redirect.NEVER, client.followRedirects());
            ThreadPoolTaskScheduler scheduler = context.getBean("chatPushScheduler", ThreadPoolTaskScheduler.class);
            assertEquals(1, scheduler.getScheduledThreadPoolExecutor().getCorePoolSize());
        }
    }
}
