package com.astro.api.chat.mapper;

import com.astro.api.chat.model.ChatMessageDocument;
import com.astro.api.chat.model.ChatPushEvent;
import com.astro.api.chat.model.ChatPushOutboxEntry;
import java.time.Instant;
import org.bson.Document;
import com.astro.api.chat.push.ChatPushParticipants;

public final class ChatPushEventMapper {
    private ChatPushEventMapper() { }

    public static ChatPushEvent from(ChatMessageDocument message, ChatPushParticipants participants) {
        return new ChatPushEvent(1, message.id(), message.conversationId(), message.senderId(),
                participants.recipient().getId(), participants.sender().getWorkspaceId(),
                participants.recipient().getFirebaseUid(), truncate(participants.sender().getName(), 80),
                truncate(message.content(), 160), message.createdAt());
    }

    public static Document toDocument(ChatPushEvent event) {
        return new Document("version", event.version()).append("messageId", event.messageId())
                .append("conversationId", event.conversationId()).append("senderId", event.senderId().toString())
                .append("recipientId", event.recipientId().toString()).append("workspaceId", event.workspaceId().toString())
                .append("recipientUid", event.recipientUid()).append("senderName", event.senderName())
                .append("preview", event.preview()).append("createdAt", event.createdAt().toString());
    }

    public static ChatPushEvent fromDocument(Document payload) {
        return new ChatPushEvent(payload.getInteger("version"), payload.getString("messageId"),
                payload.getString("conversationId"), Long.valueOf(payload.getString("senderId")),
                Long.valueOf(payload.getString("recipientId")), Long.valueOf(payload.getString("workspaceId")),
                payload.getString("recipientUid"), payload.getString("senderName"), payload.getString("preview"),
                Instant.parse(payload.getString("createdAt")));
    }

    public static ChatPushOutboxEntry fromOutboxDocument(Document document) {
        if (document == null) return null;
        ChatPushEvent event = fromDocument(document.get("payload", Document.class));
        return new ChatPushOutboxEntry(document.getString("_id"), event, document.getInteger("attempts", 1));
    }

    public static String toJson(ChatPushEvent event) {
        return toDocument(event).toJson();
    }

    public static String truncate(String value, int limit) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").strip();
        int count = normalized.codePointCount(0, normalized.length());
        if (count <= limit) return normalized;
        return normalized.substring(0, normalized.offsetByCodePoints(0, limit - 1)) + "…";
    }
}
