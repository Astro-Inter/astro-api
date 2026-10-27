package com.astro.api.chat.service;

import com.astro.api.chat.dto.response.ChatSendResponse;

public interface ChatMessagePublisher {
    void publish(Long recipientId, ChatSendResponse message);
}
