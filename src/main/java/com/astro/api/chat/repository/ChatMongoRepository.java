package com.astro.api.chat.repository;

import com.astro.api.chat.model.ChatMessageDocument;
import com.astro.api.chat.model.ConversationDocument;
import com.astro.api.chat.model.ChatMessageIds;
import com.astro.api.common.exception.ConflictException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.ReadConcern;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import java.util.Date;
import java.util.function.Function;
import org.bson.Document;
import java.time.Instant;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ChatMongoRepository {
    private static final TransactionOptions TRANSACTION_OPTIONS = TransactionOptions.builder()
            .readConcern(ReadConcern.SNAPSHOT)
            .writeConcern(WriteConcern.MAJORITY)
            .build();
    private static final String CONVERSATIONS = "conversas";
    private static final String MESSAGES = "mensagens";
    private final MongoTemplate mongoTemplate;
    private final MongoClient mongoClient;
    private final MongoDatabase mongoDatabase;

    public ChatMongoRepository(MongoTemplate mongoTemplate) {
        this(mongoTemplate, null);
    }

    @Autowired
    public ChatMongoRepository(MongoTemplate mongoTemplate, MongoClient mongoClient) {
        this.mongoTemplate = mongoTemplate;
        this.mongoClient = mongoClient;
        this.mongoDatabase = mongoTemplate.getDb();
    }

    public List<ConversationDocument> findConversationsFor(Long userId) {
        Query query = Query.query(Criteria.where("participantes_ids").is(userId).size(2));
        query.fields().include("_id").include("participantes_ids");
        return mongoTemplate.find(query, ConversationDocument.class, CONVERSATIONS);
    }

    public ConversationDocument findConversation(String conversationId) {
        Query query = Query.query(Criteria.where("_id").is(conversationId));
        query.fields().include("_id").include("participantes_ids");
        return mongoTemplate.findOne(query, ConversationDocument.class, CONVERSATIONS);
    }

    public <T> T inTransaction(Function<ClientSession, T> operation) {
        try (ClientSession session = mongoClient.startSession()) {
            return session.withTransaction(() -> operation.apply(session), TRANSACTION_OPTIONS);
        }
    }

    public ConversationDocument findConversationByPair(ClientSession session, Long firstParticipantId,
                                                        Long secondParticipantId) {
        List<Document> matches = conversations().find(session, participantPairQuery(firstParticipantId, secondParticipantId))
                .limit(2).into(new ArrayList<>());
        if (matches.size() > 1) {
            throw new ConflictException("Há mais de uma conversa para o mesmo par de participantes.");
        }
        return matches.isEmpty() ? null : conversation(matches.getFirst());
    }

    public ConversationDocument findConversationById(ClientSession session, String conversationId) {
        Document found = conversations().find(session, new Document("_id", conversationId)).first();
        return found == null ? null : conversation(found);
    }

    public ConversationDocument findConversationByPair(Long firstParticipantId, Long secondParticipantId) {
        List<Document> matches = conversations().find(participantPairQuery(firstParticipantId, secondParticipantId))
                .limit(2).into(new ArrayList<>());
        if (matches.size() > 1) {
            throw new ConflictException("Há mais de uma conversa para o mesmo par de participantes.");
        }
        return matches.isEmpty() ? null : conversation(matches.getFirst());
    }

    public ChatMessageDocument findMessageByIdempotency(Long senderId, String clientMessageId) {
        Document found = messages().find(new Document("_id", ChatMessageIds.forMessage(senderId, clientMessageId))
                .append("remetente_id", senderId)).first();
        return found == null ? null : message(found);
    }

    public ChatMessageDocument findMessage(ClientSession session, Long senderId, String clientMessageId) {
        Document found = messages().find(session, new Document("_id", ChatMessageIds.forMessage(senderId, clientMessageId))
                .append("remetente_id", senderId)).first();
        return found == null ? null : message(found);
    }

    public ChatMessageDocument findMessage(ClientSession session, String conversationId, String messageId) {
        Document found = messages().find(session, new Document("_id", messageId)
                .append("conversa_id", conversationId)).first();
        return found == null ? null : message(found);
    }

    public ChatMessageDocument findMessage(String conversationId, String messageId) {
        Document found = messages().find(new Document("_id", messageId)
                .append("conversa_id", conversationId)).first();
        return found == null ? null : message(found);
    }

    public void insertConversation(ClientSession session, ConversationDocument conversation) {
        conversations().insertOne(session, new Document("_id", conversation.id())
                .append("participantes_ids", conversation.participants())
                );
    }

    public void insertMessage(ClientSession session, ChatMessageDocument message) {
        messages().insertOne(session, new Document("_id", message.id())
                .append("conversa_id", message.conversationId())
                .append("remetente_id", message.senderId())
                .append("mensagem", message.content())
                .append("resposta_mensagem_id", message.replyToMessageId())
                .append("data", Date.from(message.createdAt())));
    }

    private ConversationDocument conversation(Document document) {
        List<?> rawParticipants = (List<?>) document.get("participantes_ids");
        List<Long> participants = rawParticipants == null ? List.of() : rawParticipants.stream()
                .map(value -> ((Number) value).longValue()).toList();
        return new ConversationDocument(document.getString("_id"), participants);
    }

    private ChatMessageDocument message(Document document) {
        Date date = document.getDate("data");
        return new ChatMessageDocument(document.getString("_id"), document.getString("conversa_id"),
                ((Number) document.get("remetente_id")).longValue(), document.getString("mensagem"),
                document.getString("resposta_mensagem_id"), date.toInstant());
    }

    private static Document participantPairQuery(Long firstParticipantId, Long secondParticipantId) {
        return new Document("participantes_ids", new Document("$all", List.of(firstParticipantId, secondParticipantId))
                .append("$size", 2));
    }

    private MongoCollection<Document> conversations() { return mongoDatabase.getCollection(CONVERSATIONS); }
    private MongoCollection<Document> messages() { return mongoDatabase.getCollection(MESSAGES); }

    public List<ChatMessageDocument> findMessagesBefore(String conversationId, Instant cursorDate,
                                                        String cursorMessageId, int limit) {
        Criteria criteria = Criteria.where("conversa_id").is(conversationId);
        if (cursorDate != null && cursorMessageId != null) {
            criteria = criteria.andOperator(cursorCriteria(cursorDate, cursorMessageId));
        }
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Order.desc("data"), Sort.Order.desc("_id")))
                .limit(limit);
        query.fields().include("_id").include("remetente_id").include("mensagem")
                .include("resposta_mensagem_id").include("data");
        return mongoTemplate.find(query, ChatMessageDocument.class, MESSAGES);
    }

    private Criteria cursorCriteria(Instant cursorDate, String cursorMessageId) {
        return new Criteria().orOperator(
                Criteria.where("data").lt(cursorDate),
                new Criteria().andOperator(
                        Criteria.where("data").is(cursorDate),
                        Criteria.where("_id").lt(cursorMessageId)));
    }

    public List<LastMessageProjection> findLatestMessages(Collection<String> conversationIds) {
        if (conversationIds.isEmpty()) {
            return List.of();
        }
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("conversa_id").in(conversationIds)),
                Aggregation.sort(Sort.by(Sort.Order.desc("data"), Sort.Order.desc("_id"))),
                Aggregation.group("conversa_id")
                        .first("remetente_id").as("senderId")
                        .first("mensagem").as("content")
                        .first("data").as("date"),
                Aggregation.project("senderId", "content", "date")
                        .and("_id").as("conversationId")
        );
        AggregationResults<LastMessageProjection> results = mongoTemplate.aggregate(
                aggregation, MESSAGES, LastMessageProjection.class);
        return results.getMappedResults();
    }
}
