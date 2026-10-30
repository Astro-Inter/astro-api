package com.astro.api.chat.mapper;

import com.astro.api.chat.model.ChatMessageDocument;
import com.astro.api.chat.push.ChatPushEvent;
import com.astro.api.chat.push.ChatPushParticipants;

public final class ChatPushEventMapper {
    private ChatPushEventMapper() { }

    public static ChatPushEvent from(ChatMessageDocument message, ChatPushParticipants participants) {
        return new ChatPushEvent(1, message.id(), message.conversationId(), message.senderId().toString(),
                participants.recipient().getId().toString(), participants.sender().getWorkspaceId().toString(),
                participants.recipient().getFirebaseUid(), truncate(participants.sender().getName(), 80),
                truncate(message.content(), 160), message.createdAt());
    }

    public static String truncate(String value, int limit) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").strip();
        int count = normalized.codePointCount(0, normalized.length());
        if (count <= limit) return normalized;
        return normalized.substring(0, normalized.offsetByCodePoints(0, limit - 1)) + "…";
    }
}
