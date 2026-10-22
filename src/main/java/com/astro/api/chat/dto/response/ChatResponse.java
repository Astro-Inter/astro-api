package com.astro.api.chat.dto.response;

public record ChatResponse(
        String id,
        ChatParticipantResponse participant,
        ChatLastMessageResponse lastMessage,
        int unreadCount
) { }
