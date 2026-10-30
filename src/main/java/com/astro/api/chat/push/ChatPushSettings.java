package com.astro.api.chat.push;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record ChatPushSettings(
        @Value("${astro.chat.push.enabled:false}") boolean enabled,
        @Value("${astro.chat.push.url:}") String url,
        @Value("${astro.chat.push.secret:}") String secret) {
    @Override public String toString() { return "ChatPushSettings[enabled=" + enabled + "]"; }
}
