package com.astro.api.chat.messaging;

import com.astro.api.chat.dto.response.ChatTypingResponse;

public record ChatTypingDeliveryEvent(Long recipientId, ChatTypingResponse event) { }
