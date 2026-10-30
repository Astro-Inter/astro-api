package com.astro.api.chat.push;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import java.time.Instant;
import java.util.Date;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChatPushOutbox {
    private final MongoCollection<Document> events;
    private boolean initialized;

    public ChatPushOutbox(MongoTemplate template) {
        this.events = template.getDb().getCollection("chat_push_outbox");
    }

    public void insert(ClientSession session, ChatPushEvent event) {
        Document payload = new Document("version", event.version()).append("messageId", event.messageId())
                .append("conversationId", event.conversationId()).append("senderId", event.senderId())
                .append("recipientId", event.recipientId()).append("workspaceId", event.workspaceId())
                .append("recipientUid", event.recipientUid()).append("senderName", event.senderName())
                .append("preview", event.preview()).append("createdAt", event.createdAt().toString());
        events.insertOne(session, new Document("_id", event.messageId()).append("payload", payload)
                .append("nextAttempt", Date.from(event.createdAt())).append("expiresAt", Date.from(event.createdAt().plusSeconds(86400)))
                .append("attempts", 0).append("done", false));
    }

    public Document claim(Instant now, String lease) {
        initialize();
        return events.findOneAndUpdate(new Document("done", false)
                        .append("nextAttempt", new Document("$lte", Date.from(now)))
                        .append("expiresAt", new Document("$gt", Date.from(now))),
                new Document("$set", new Document("lease", lease).append("nextAttempt", Date.from(now.plusSeconds(120))))
                        .append("$inc", new Document("attempts", 1)),
                new FindOneAndUpdateOptions().sort(new Document("nextAttempt", 1)).returnDocument(ReturnDocument.AFTER));
    }

    private synchronized void initialize() {
        if (initialized) return;
        events.createIndex(new Document("done", 1).append("nextAttempt", 1));
        events.createIndex(new Document("expiresAt", 1),
                new com.mongodb.client.model.IndexOptions().expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS));
        initialized = true;
    }

    public void complete(String id, String lease) {
        events.updateOne(new Document("_id", id).append("lease", lease),
                new Document("$set", new Document("done", true)).append("$unset", new Document("payload", "").append("lease", "")));
    }

    public void retry(String id, String lease, Instant when) {
        events.updateOne(new Document("_id", id).append("lease", lease),
                new Document("$set", new Document("nextAttempt", Date.from(when))).append("$unset", new Document("lease", "")));
    }
}
