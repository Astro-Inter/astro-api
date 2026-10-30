package com.astro.api.chat.push;

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
        when(database.getCollection("chat_push_outbox")).thenReturn(events);
        return new ChatPushOutbox(template);
    }

    @Test void writesIdempotentMessageKeyAndExpirationInTheSuppliedSession() {
        ChatPushOutbox outbox = outbox();
        ClientSession session = mock(ClientSession.class);
        Instant now = Instant.parse("2026-10-30T10:00:00Z");
        outbox.insert(session, new ChatPushEvent(1, "message", "conversation", "1", "2", "10",
                "uid-2", "Gestor", "Olá", now));
        ArgumentCaptor<Document> document = ArgumentCaptor.forClass(Document.class);
        verify(events).insertOne(eq(session), document.capture());
        assertEquals("message", document.getValue().getString("_id"));
        assertEquals(now.plusSeconds(86400), document.getValue().getDate("expiresAt").toInstant());
        assertEquals("Olá", document.getValue().get("payload", Document.class).getString("preview"));
    }

    @Test void completionRequiresCurrentLeaseAndRemovesPreview() {
        ChatPushOutbox outbox = outbox();
        outbox.complete("message", "lease");
        ArgumentCaptor<Document> filter = ArgumentCaptor.forClass(Document.class);
        ArgumentCaptor<Document> update = ArgumentCaptor.forClass(Document.class);
        verify(events).updateOne(filter.capture(), update.capture());
        assertEquals(new Document("_id", "message").append("lease", "lease"), filter.getValue());
        assertTrue(update.getValue().get("$unset", Document.class).containsKey("payload"));
        assertTrue(update.getValue().get("$set", Document.class).getBoolean("done"));
    }

    @Test void claimExcludesExpiredEventsAndLeasesAtomically() {
        ChatPushOutbox outbox = outbox();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        outbox.claim(now, "lease");
        ArgumentCaptor<Document> filter = ArgumentCaptor.forClass(Document.class);
        ArgumentCaptor<Document> update = ArgumentCaptor.forClass(Document.class);
        verify(events).findOneAndUpdate(filter.capture(), update.capture(), any(com.mongodb.client.model.FindOneAndUpdateOptions.class));
        assertEquals(false, filter.getValue().getBoolean("done"));
        assertEquals(java.util.Date.from(now), filter.getValue().get("expiresAt", Document.class).get("$gt"));
        assertEquals("lease", update.getValue().get("$set", Document.class).getString("lease"));
        assertEquals(now.plusSeconds(120), update.getValue().get("$set", Document.class).getDate("nextAttempt").toInstant());
    }
}
