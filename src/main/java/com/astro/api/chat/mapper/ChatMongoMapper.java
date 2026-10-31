package com.astro.api.chat.mapper;

import com.astro.api.chat.model.ChatMessageDocument;
import com.astro.api.chat.model.ConversationDocument;
import java.util.Date;
import java.util.List;
import org.bson.Document;

public final class ChatMongoMapper {
    private ChatMongoMapper() {}

    public static ConversationDocument toConversation(Document document) {
        if (document == null) return null;
        List<?> rawParticipants = (List<?>) document.get("participantes_ids");
        List<Long> participants = List.of();
        if (rawParticipants != null) {
            participants = rawParticipants.stream().map(value -> ((Number) value).longValue()).toList();
        }
        return new ConversationDocument(document.getString("_id"), participants);
    }

    public static ChatMessageDocument toMessage(Document document) {
        if (document == null) return null;
        Date date = document.getDate("data");
        return new ChatMessageDocument(document.getString("_id"), document.getString("conversa_id"),
                ((Number) document.get("remetente_id")).longValue(), document.getString("mensagem"),
                document.getString("resposta_mensagem_id"), date.toInstant());
    }

    public static Document toDocument(ConversationDocument conversation) {
        return new Document("_id", conversation.id()).append("participantes_ids", conversation.participants());
    }

    public static Document toDocument(ChatMessageDocument message) {
        return new Document("_id", message.id()).append("conversa_id", message.conversationId())
                .append("remetente_id", message.senderId()).append("mensagem", message.content())
                .append("resposta_mensagem_id", message.replyToMessageId())
                .append("data", Date.from(message.createdAt()));
    }
}
