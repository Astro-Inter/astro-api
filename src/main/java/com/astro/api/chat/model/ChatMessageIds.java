package com.astro.api.chat.model;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ChatMessageIds {
    private ChatMessageIds() { }

    public static String forConversation(Long firstParticipantId, Long secondParticipantId) {
        long lowerId = Math.min(firstParticipantId, secondParticipantId);
        long higherId = Math.max(firstParticipantId, secondParticipantId);
        return UUID.nameUUIDFromBytes(("astro-chat-conversation:" + lowerId + ":" + higherId)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static String forMessage(Long senderId, String clientMessageId) {
        return UUID.nameUUIDFromBytes(("astro-chat-message:" + senderId + ":" + clientMessageId)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }
}
