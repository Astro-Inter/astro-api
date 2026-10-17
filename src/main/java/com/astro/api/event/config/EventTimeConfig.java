package com.astro.api.event.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventTimeConfig {
    @Bean
    public Clock eventClock() {
        return Clock.systemDefaultZone();
    }
}
