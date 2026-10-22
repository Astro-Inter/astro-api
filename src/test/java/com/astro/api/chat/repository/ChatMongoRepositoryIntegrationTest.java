package com.astro.api.chat.repository;

import com.astro.api.chat.model.ConversationDocument;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "CHAT_TEST_MONGODB_URI", matches = "mongodb://(127\\.0\\.0\\.1|localhost):[0-9]+")
class ChatMongoRepositoryIntegrationTest {
    private static MongoClient client;
    private static MongoTemplate mongo;
    private static ChatMongoRepository repository;
    private static final String DATABASE = "chat_test_" + UUID.randomUUID().toString().replace("-", "");

    @BeforeAll
    static void connect() {
        client = MongoClients.create(System.getenv("CHAT_TEST_MONGODB_URI"));
        mongo = new MongoTemplate(client, DATABASE);
        repository = new ChatMongoRepository(mongo);
    }

    @AfterAll
    static void close() {
        if (mongo != null && DATABASE.matches("chat_test_[a-f0-9]{32}")) mongo.getDb().drop();
        if (client != null) client.close();
    }

    @BeforeEach
    void clearCollections() {
        mongo.dropCollection("conversas");
        mongo.dropCollection("mensagens");
    }

    @Test
    void filtersMembershipAndReturnsOnlyTheLatestProjectedMessagePerConversation() {
        String conversationId = "11111111-2222-4333-8444-555555555555";
        String unrelatedId = "44444444-5555-4666-8777-888888888888";
        mongo.insert(new Document("_id", conversationId).append("participantes", List.of(1L, 2L)), "conversas");
        mongo.insert(new Document("_id", unrelatedId).append("participantes", List.of(3L, 4L)), "conversas");

        Instant older = Instant.parse("2026-10-20T10:00:00Z");
        Instant newer = Instant.parse("2026-10-21T10:00:00Z");
        mongo.insert(message("10000000-0000-4000-8000-000000000001", conversationId, 1L,
                "mensagem antiga", null, older), "mensagens");
        mongo.insert(message("10000000-0000-4000-8000-000000000002", conversationId, 2L,
                "mensagem atual", "33333333-4444-4555-8666-777777777777", newer), "mensagens");
        mongo.insert(message("10000000-0000-4000-8000-000000000003", unrelatedId, 4L,
                "fora do resultado", null, newer.plusSeconds(1)), "mensagens");

        List<ConversationDocument> conversations = repository.findConversationsFor(1L);
        List<LastMessageProjection> messages = repository.findLatestMessages(List.of(conversationId));

        assertEquals(List.of(conversationId), conversations.stream().map(ConversationDocument::id).toList());
        assertEquals(1, messages.size());
        assertEquals(conversationId, messages.getFirst().conversationId());
        assertEquals(2L, messages.getFirst().senderId());
        assertEquals("mensagem atual", messages.getFirst().content());
        assertEquals(newer, messages.getFirst().date());
        assertEquals(3, mongo.getCollection("mensagens").countDocuments(),
                "GET queries must leave every MongoDB message untouched");
    }

    @Test
    void projectsLatestMessageWithoutLoadingTheOriginalReply() {
        String conversationId = "11111111-2222-4333-8444-555555555555";
        mongo.insert(message("10000000-0000-4000-8000-000000000001", conversationId, 1L,
                "sem reply", null, Instant.parse("2026-10-21T10:00:00Z")), "mensagens");

        LastMessageProjection message = repository.findLatestMessages(List.of(conversationId)).getFirst();

        assertEquals("sem reply", message.content());
        assertEquals(1, mongo.getCollection("mensagens").countDocuments());
    }

    private Document message(String id, String conversationId, Long senderId, String content,
                             String replyId, Instant date) {
        return new Document("_id", id)
                .append("id_conversa", conversationId)
                .append("id_remetente", senderId)
                .append("mensagem", content)
                .append("resposta_mensagem_id", replyId)
                .append("data", java.util.Date.from(date));
    }
}
