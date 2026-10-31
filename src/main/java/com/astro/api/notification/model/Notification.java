package com.astro.api.notification.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.MongoId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "notificacoes")
@Getter
@Setter
@NoArgsConstructor
public class Notification {
    @MongoId
    private Object id;

    @Field("usuario_id")
    private Long userId;

    @Field("mensagem")
    private String message;

    @Field("titulo")
    private String title;

    @Field("evento_id")
    private Long eventId;

    @Field("data_criacao")
    private Instant createdAt;
}
