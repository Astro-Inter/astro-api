package com.astro.api.chat.repository;

import com.astro.api.chat.model.ChatMessageDocument;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMongoRepositoryTest {
    private static final String CONVERSATION_ID = "11111111-2222-4333-8444-555555555555";
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final ChatMongoRepository repository = new ChatMongoRepository(mongoTemplate);

    @Test
    void findsConversationByIdAndProjectsOnlyIdAndParticipants() {
        when(mongoTemplate.findOne(any(Query.class),
                eq(com.astro.api.chat.model.ConversationDocument.class), eq("conversas"))).thenReturn(null);

        repository.findConversation(CONVERSATION_ID);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findOne(queryCaptor.capture(),
                eq(com.astro.api.chat.model.ConversationDocument.class), eq("conversas"));
        Query query = queryCaptor.getValue();
        assertEquals(CONVERSATION_ID, query.getQueryObject().getString("_id"));
        assertTrue(query.getFieldsObject().containsKey("_id"));
        assertTrue(query.getFieldsObject().containsKey("participantes_ids"));
        assertEquals(2, query.getFieldsObject().size());
    }

    @Test
    void buildsRecentMessagesQueryWithProjectionSortAndLimit() {
        when(mongoTemplate.find(any(Query.class), eq(ChatMessageDocument.class), eq("mensagens")))
                .thenReturn(List.of());

        repository.findMessagesBefore(CONVERSATION_ID, null, null, 31);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(ChatMessageDocument.class), eq("mensagens"));
        Query query = queryCaptor.getValue();
        assertEquals(CONVERSATION_ID, query.getQueryObject().getString("conversa_id"));
        assertEquals(new Document("data", -1).append("_id", -1), query.getSortObject());
        assertEquals(31, query.getLimit());
        assertTrue(query.getFieldsObject().containsKey("remetente_id"));
        assertTrue(query.getFieldsObject().containsKey("mensagem"));
        assertTrue(query.getFieldsObject().containsKey("resposta_mensagem_id"));
        assertTrue(query.getFieldsObject().containsKey("data"));
    }

    @Test
    void buildsStrictCompositeCursorQueryForEqualDates() {
        Instant cursorDate = Instant.parse("2026-10-21T10:00:00Z");
        String cursorMessageId = "10000000-0000-4000-8000-000000000002";
        when(mongoTemplate.find(any(Query.class), eq(ChatMessageDocument.class), eq("mensagens")))
                .thenReturn(List.of());

        repository.findMessagesBefore(CONVERSATION_ID, cursorDate, cursorMessageId, 31);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(ChatMessageDocument.class), eq("mensagens"));
        Document query = queryCaptor.getValue().getQueryObject();
        assertEquals(CONVERSATION_ID, query.getString("conversa_id"));
        List<Document> andTerms = (List<Document>) query.get("$and");
        Document cursor = andTerms.getFirst();
        List<Document> orTerms = (List<Document>) cursor.get("$or");
        assertEquals(cursorDate, ((Document) orTerms.get(0).get("data")).get("$lt"));
        List<Document> sameDateTerms = (List<Document>) orTerms.get(1).get("$and");
        assertEquals(cursorDate, sameDateTerms.get(0).get("data"));
        assertEquals(cursorMessageId, ((Document) sameDateTerms.get(1).get("_id")).get("$lt"));
    }
    @Test
    void messageLookupKeepsConversationScopeInsideTheSuppliedSession() {
        com.mongodb.client.MongoDatabase database = mock(com.mongodb.client.MongoDatabase.class);
        com.mongodb.client.MongoCollection<Document> messages = mock(com.mongodb.client.MongoCollection.class);
        com.mongodb.client.FindIterable<Document> results = mock(com.mongodb.client.FindIterable.class);
        com.mongodb.client.ClientSession session = mock(com.mongodb.client.ClientSession.class);
        when(mongoTemplate.getDb()).thenReturn(database);
        when(database.getCollection("mensagens")).thenReturn(messages);
        when(messages.find(eq(session), any(Document.class))).thenReturn(results);
        when(results.first()).thenReturn(null);
        ChatMongoRepository scopedRepository = new ChatMongoRepository(mongoTemplate);

        org.junit.jupiter.api.Assertions.assertNull(scopedRepository.findMessage(session, CONVERSATION_ID, "message"));
        verify(messages).find(session, new Document("_id", "message").append("conversa_id", CONVERSATION_ID));
    }

    @Test
    void pairLookupReturnsBothMatchesForTheServiceToValidateUniqueness() {
        com.mongodb.client.MongoDatabase database = mock(com.mongodb.client.MongoDatabase.class);
        com.mongodb.client.MongoCollection<Document> conversations = mock(com.mongodb.client.MongoCollection.class);
        com.mongodb.client.FindIterable<Document> results = mock(com.mongodb.client.FindIterable.class);
        com.mongodb.client.ClientSession session = mock(com.mongodb.client.ClientSession.class);
        when(mongoTemplate.getDb()).thenReturn(database);
        when(database.getCollection("conversas")).thenReturn(conversations);
        when(conversations.find(eq(session), any(Document.class))).thenReturn(results);
        when(results.limit(2)).thenReturn(results);
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.Collection<Document> target = invocation.getArgument(0);
            target.addAll(List.of(
                    new Document("_id", "first").append("participantes_ids", List.of(1, 2)),
                    new Document("_id", "second").append("participantes_ids", List.of(1L, 2L))));
            return target;
        }).when(results).into(any(java.util.Collection.class));
        ChatMongoRepository scopedRepository = new ChatMongoRepository(mongoTemplate);

        assertEquals(2, scopedRepository.findConversationsByPair(session, 1L, 2L).size());
        verify(results).limit(2);
        verify(conversations).find(session, new Document("participantes_ids",
                new Document("$all", List.of(1L, 2L)).append("$size", 2)));
    }

}
