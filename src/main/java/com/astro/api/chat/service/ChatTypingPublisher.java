package com.astro.api.chat.service;

import com.astro.api.chat.dto.response.ChatTypingResponse;

public interface ChatTypingPublisher {
    void publish(Long recipientId, ChatTypingResponse event);
}
