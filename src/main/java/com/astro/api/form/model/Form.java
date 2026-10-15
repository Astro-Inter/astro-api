package com.astro.api.form.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Document(collection = "formularios")
@Getter
@Setter
@NoArgsConstructor
public class Form {
    @Id
    private Long id;

    @Field("id_gestor")
    private Long managerId;

    @Field("id_gestor_remetente")
    private Long senderManagerId;

    @Field("titulo")
    private String title;

    @Field("descricao")
    private String description;

    @Field("data_limite")
    private Instant dueDate;

    @Field("perguntas")
    private List<FormQuestion> questions;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class FormQuestion {
        @Field("id_pergunta")
        private Long id;

        @Field("tipo")
        private String type;

        @Field("pergunta")
        private String question;

        @Field("resposta")
        private List<Object> answer;

        @Field("opcoes")
        private List<String> options;

        @Field("obrigatoria")
        private Boolean required;
    }
}
