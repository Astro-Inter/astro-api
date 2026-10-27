package com.astro.api.chat.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.util.List;

@Document("conversas")
public record ConversationDocument(
        @Id String id,
        @Field("participantes") List<Long> participants
) { }
