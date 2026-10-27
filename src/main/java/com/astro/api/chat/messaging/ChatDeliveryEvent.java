package com.astro.api.chat.messaging;

import com.astro.api.chat.dto.response.ChatSendResponse;

public record ChatDeliveryEvent(Long recipientId, ChatSendResponse message) { }
