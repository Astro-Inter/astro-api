package com.astro.api.chat.mapper;

import com.astro.api.chat.model.ChatMessageDocument;
import com.astro.api.chat.model.ConversationDocument;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatMongoMapperTest {
    @Test
    void absentResultsRemainAbsentAndMissingParticipantsBecomeEmpty() {
        assertNull(ChatMongoMapper.toConversation(null));
        assertNull(ChatMongoMapper.toMessage(null));
        assertEquals(List.of(), ChatMongoMapper.toConversation(new Document("_id", "conversation")).participants());
    }

    @Test
    void conversationRoundTripPreservesIdentifiersAndNormalizesBsonNumbers() {
        Document document = new Document("_id", "conversation").append("participantes_ids", List.of(1, 2L));
        ConversationDocument conversation = ChatMongoMapper.toConversation(document);
        assertEquals(new ConversationDocument("conversation", List.of(1L, 2L)), conversation);
        assertEquals(conversation, ChatMongoMapper.toConversation(ChatMongoMapper.toDocument(conversation)));
    }

    @Test
    void messageRoundTripPreservesReplyScopeAndBsonDate() {
        Instant createdAt = Instant.parse("2026-10-31T12:00:00Z");
        ChatMessageDocument message = new ChatMessageDocument("message", "conversation", 1L,
                "Olá", "reply", createdAt);
        Document document = ChatMongoMapper.toDocument(message);
        assertEquals("conversation", document.getString("conversa_id"));
        assertEquals("reply", document.getString("resposta_mensagem_id"));
        assertEquals(createdAt, document.getDate("data").toInstant());
        document.put("remetente_id", 1);
        assertEquals(message, ChatMongoMapper.toMessage(document));
    }
}
