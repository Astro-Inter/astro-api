package com.astro.api.chat.mapper;

import com.astro.api.chat.model.ChatPushEvent;
import com.astro.api.chat.model.ChatPushOutboxEntry;
import java.time.Instant;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatPushEventMapperTest {
    private ChatPushEvent event() {
        return new ChatPushEvent(1, "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
                "11111111-2222-4333-8444-555555555555", 9007199254740993L, 2L, 10L,
                "uid-2", "Gestor", "Olá, \"Astro\"! 😀", Instant.parse("2026-10-30T10:00:00.123456Z"));
    }

    @Test void preservesExistingJsonContractAndNumericPrecision() {
        ChatPushEvent original = event();
        Document payload = Document.parse(ChatPushEventMapper.toJson(original));
        assertEquals(10, payload.size());
        assertEquals(1, payload.getInteger("version"));
        assertEquals("9007199254740993", payload.getString("senderId"));
        assertEquals("2", payload.getString("recipientId"));
        assertEquals("10", payload.getString("workspaceId"));
        assertEquals(original.createdAt().toString(), payload.getString("createdAt"));
        assertEquals(original.preview(), payload.getString("preview"));
        assertEquals(original, ChatPushEventMapper.fromDocument(payload));
    }

    @Test void mapsClaimedDocumentToTypedEntryWithRetryCount() {
        Document document = new Document("_id", event().messageId())
                .append("evento", ChatPushEventMapper.toStoredDocument(event())).append("tentativas", 3);
        ChatPushOutboxEntry entry = ChatPushEventMapper.fromOutboxDocument(document);
        assertEquals(event().messageId(), entry.id());
        assertEquals(event(), entry.event());
        assertEquals(3, entry.attempts());
    }

    @Test void handlesNoClaimAndLegacyDefaultAttemptCount() {
        assertNull(ChatPushEventMapper.fromOutboxDocument(null));
        Document document = new Document("_id", event().messageId())
                .append("evento", ChatPushEventMapper.toStoredDocument(event()));
        assertEquals(1, ChatPushEventMapper.fromOutboxDocument(document).attempts());
    }
}
