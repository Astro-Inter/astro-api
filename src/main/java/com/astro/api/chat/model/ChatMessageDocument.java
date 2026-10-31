package com.astro.api.chat.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document("mensagens")
public record ChatMessageDocument(
        @Id String id,
        @Field("conversa_id") String conversationId,
        @Field("remetente_id") Long senderId,
        @Field("mensagem") String content,
        @Field("resposta_mensagem_id") String replyToMessageId,
        @Field("data") Instant createdAt
) { }
