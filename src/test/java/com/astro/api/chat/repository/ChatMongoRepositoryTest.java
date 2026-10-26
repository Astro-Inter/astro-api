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
    void buildsRecentMessagesQueryWithProjectionSortAndLimit() {
        when(mongoTemplate.find(any(Query.class), eq(ChatMessageDocument.class), eq("mensagens")))
                .thenReturn(List.of());

        repository.findMessagesBefore(CONVERSATION_ID, null, null, 31);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(ChatMessageDocument.class), eq("mensagens"));
        Query query = queryCaptor.getValue();
        assertEquals(CONVERSATION_ID, query.getQueryObject().getString("id_conversa"));
        assertEquals(new Document("data", -1).append("_id", -1), query.getSortObject());
        assertEquals(31, query.getLimit());
        assertTrue(query.getFieldsObject().containsKey("id_remetente"));
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
        assertEquals(CONVERSATION_ID, query.getString("id_conversa"));
        List<Document> andTerms = (List<Document>) query.get("$and");
        Document cursor = andTerms.getFirst();
        List<Document> orTerms = (List<Document>) cursor.get("$or");
        assertEquals(cursorDate, ((Document) orTerms.get(0).get("data")).get("$lt"));
        List<Document> sameDateTerms = (List<Document>) orTerms.get(1).get("$and");
        assertEquals(cursorDate, sameDateTerms.get(0).get("data"));
        assertEquals(cursorMessageId, ((Document) sameDateTerms.get(1).get("_id")).get("$lt"));
    }
}
