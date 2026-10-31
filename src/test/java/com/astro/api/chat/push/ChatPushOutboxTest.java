package com.astro.api.chat.push;

import com.astro.api.chat.model.ChatPushEvent;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.time.Instant;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatPushOutboxTest {
    private final MongoTemplate template = mock(MongoTemplate.class);
    private final MongoDatabase database = mock(MongoDatabase.class);
    private final MongoCollection<Document> events = mock(MongoCollection.class);

    private ChatPushOutbox outbox() {
        when(template.getDb()).thenReturn(database);
        when(database.getCollection("fila_entregas_chat")).thenReturn(events);
        return new ChatPushOutbox(template);
    }

    @Test void writesIdempotentMessageKeyAndExpirationInTheSuppliedSession() {
        ChatPushOutbox outbox = outbox();
        ClientSession session = mock(ClientSession.class);
        Instant now = Instant.parse("2026-10-30T10:00:00Z");
        outbox.insert(session, new ChatPushEvent(1, "message", "conversation", 1L, 2L, 10L,
                "uid-2", "Gestor", "Olá", now));
        ArgumentCaptor<Document> document = ArgumentCaptor.forClass(Document.class);
        verify(events).insertOne(eq(session), document.capture());
        assertEquals("message", document.getValue().getString("_id"));
        assertEquals(now.plusSeconds(86400), document.getValue().getDate("expira_em").toInstant());
        assertEquals("Olá", document.getValue().get("evento", Document.class).getString("previa"));
    }

    @Test void completionRequiresCurrentLeaseAndRemovesPreview() {
        ChatPushOutbox outbox = outbox();
        outbox.complete("message", "reserva_token");
        ArgumentCaptor<Document> filter = ArgumentCaptor.forClass(Document.class);
        ArgumentCaptor<Document> update = ArgumentCaptor.forClass(Document.class);
        verify(events).updateOne(filter.capture(), update.capture());
        assertEquals(new Document("_id", "message").append("reserva_token", "reserva_token"), filter.getValue());
        assertTrue(update.getValue().get("$unset", Document.class).containsKey("evento"));
        assertTrue(update.getValue().get("$set", Document.class).getBoolean("concluida"));
    }

    @Test void claimExcludesExpiredEventsAndLeasesAtomically() {
        ChatPushOutbox outbox = outbox();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        outbox.claim(now, "reserva_token");
        ArgumentCaptor<Document> filter = ArgumentCaptor.forClass(Document.class);
        ArgumentCaptor<Document> update = ArgumentCaptor.forClass(Document.class);
        verify(events).findOneAndUpdate(filter.capture(), update.capture(), any(com.mongodb.client.model.FindOneAndUpdateOptions.class));
        assertEquals(false, filter.getValue().getBoolean("concluida"));
        assertEquals(java.util.Date.from(now), filter.getValue().get("expira_em", Document.class).get("$gt"));
        assertEquals("reserva_token", update.getValue().get("$set", Document.class).getString("reserva_token"));
        assertEquals(now.plusSeconds(120), update.getValue().get("$set", Document.class).getDate("proxima_tentativa_em").toInstant());
    }

    @Test void repeatedClaimsInitializeIndexesOnlyOnce() {
        ChatPushOutbox outbox = outbox();
        outbox.claim(Instant.now(), "first");
        outbox.claim(Instant.now(), "second");
        verify(events, times(1)).createIndex(new Document("concluida", 1).append("proxima_tentativa_em", 1));
        verify(events, times(1)).createIndex(eq(new Document("expira_em", 1)),
                any(com.mongodb.client.model.IndexOptions.class));
    }

    @Test void failedIndexInitializationIsRetriedOnNextClaim() {
        ChatPushOutbox outbox = outbox();
        when(events.createIndex(any(Document.class))).thenThrow(new IllegalStateException("unavailable")).thenReturn("index");
        assertThrows(IllegalStateException.class, () -> outbox.claim(Instant.now(), "first"));
        outbox.claim(Instant.now(), "second");
        verify(events, times(2)).createIndex(new Document("concluida", 1).append("proxima_tentativa_em", 1));
        verify(events).findOneAndUpdate(any(Document.class), any(Document.class),
                any(com.mongodb.client.model.FindOneAndUpdateOptions.class));
    }
}
