package com.astro.api.chat.push;

import com.astro.api.chat.model.ChatPushEvent;
import com.astro.api.chat.model.ChatPushOutboxEntry;
import com.astro.api.chat.mapper.ChatPushEventMapper;
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
        this.events = template.getDb().getCollection("fila_entregas_chat");
    }

    public void insert(ClientSession session, ChatPushEvent event) {
        Document payload = ChatPushEventMapper.toStoredDocument(event);
        events.insertOne(session, new Document("_id", event.messageId()).append("evento", payload)
                .append("proxima_tentativa_em", Date.from(event.createdAt())).append("expira_em", Date.from(event.createdAt().plusSeconds(86400)))
                .append("tentativas", 0).append("concluida", false));
    }

    public ChatPushOutboxEntry claim(Instant now, String lease) {
        initialize();
        Document document = events.findOneAndUpdate(new Document("concluida", false)
                        .append("proxima_tentativa_em", new Document("$lte", Date.from(now)))
                        .append("expira_em", new Document("$gt", Date.from(now))),
                new Document("$set", new Document("reserva_token", lease).append("proxima_tentativa_em", Date.from(now.plusSeconds(120))))
                        .append("$inc", new Document("tentativas", 1)),
                new FindOneAndUpdateOptions().sort(new Document("proxima_tentativa_em", 1)).returnDocument(ReturnDocument.AFTER));
        return ChatPushEventMapper.fromOutboxDocument(document);
    }

    private void initialize() {
        if (initialized) return;
        events.createIndex(new Document("concluida", 1).append("proxima_tentativa_em", 1));
        events.createIndex(new Document("expira_em", 1),
                new com.mongodb.client.model.IndexOptions().expireAfter(0L, java.util.concurrent.TimeUnit.SECONDS));
        initialized = true;
    }

    public void complete(String id, String lease) {
        events.updateOne(new Document("_id", id).append("reserva_token", lease),
                new Document("$set", new Document("concluida", true)).append("$unset", new Document("evento", "").append("reserva_token", "")));
    }

    public void retry(String id, String lease, Instant when) {
        events.updateOne(new Document("_id", id).append("reserva_token", lease),
                new Document("$set", new Document("proxima_tentativa_em", Date.from(when))).append("$unset", new Document("reserva_token", "")));
    }
}
