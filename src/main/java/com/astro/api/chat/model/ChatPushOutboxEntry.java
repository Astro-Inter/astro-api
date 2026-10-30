package com.astro.api.chat.model;

public record ChatPushOutboxEntry(String id, ChatPushEvent event, int attempts) { }
