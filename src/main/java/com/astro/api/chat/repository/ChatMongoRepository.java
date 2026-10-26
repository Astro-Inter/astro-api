package com.astro.api.chat.repository;

import com.astro.api.chat.model.ChatMessageDocument;
import com.astro.api.chat.model.ConversationDocument;
import java.time.Instant;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public class ChatMongoRepository {
    private static final String CONVERSATIONS = "conversas";
    private static final String MESSAGES = "mensagens";
    private final MongoTemplate mongoTemplate;

    public ChatMongoRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<ConversationDocument> findConversationsFor(Long userId) {
        Query query = Query.query(Criteria.where("participantes").is(userId).size(2));
        query.fields().include("_id").include("participantes");
        return mongoTemplate.find(query, ConversationDocument.class, CONVERSATIONS);
    }

    public ConversationDocument findConversation(String conversationId) {
        return mongoTemplate.findById(conversationId, ConversationDocument.class, CONVERSATIONS);
    }

    public List<ChatMessageDocument> findMessagesBefore(String conversationId, Instant cursorDate,
                                                        String cursorMessageId, int limit) {
        Criteria criteria = Criteria.where("id_conversa").is(conversationId);
        if (cursorDate != null && cursorMessageId != null) {
            criteria = criteria.andOperator(cursorCriteria(cursorDate, cursorMessageId));
        }
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Order.desc("data"), Sort.Order.desc("_id")))
                .limit(limit);
        query.fields().include("_id").include("id_remetente").include("mensagem")
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
                Aggregation.match(Criteria.where("id_conversa").in(conversationIds)),
                Aggregation.sort(Sort.by(Sort.Order.desc("data"), Sort.Order.desc("_id"))),
                Aggregation.group("id_conversa")
                        .first("id_remetente").as("senderId")
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
