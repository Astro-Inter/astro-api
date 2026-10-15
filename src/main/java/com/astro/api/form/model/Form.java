package com.astro.api.form.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.convert.ValueConverter;
import com.astro.api.form.mapper.FormStatusConverter;

import java.time.Instant;
import java.util.List;

@Document(collection = "formularios")
@Getter
@Setter
@NoArgsConstructor
public class Form {
    @Id
    private Long id;

    @Field("id_workspace")
    private Long workspaceId;

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

    @ValueConverter(FormStatusConverter.class)
    private FormStatus status;

    @Field("criado_em")
    private Instant createdAt;

    @Field("atualizado_em")
    private Instant updatedAt;

    @Field("respondido_em")
    private Instant respondedAt;

    @Field("nr")
    private Integer nrCode;

    @Field("id_unidade")
    private Long unitId;

    @Field("versao")
    private Long version;

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
        private List<FormOption> options;

        @Field("obrigatoria")
        private Boolean required;

        @Field("multipla_escolha")
        private Boolean multiple;

        @Field("maximo_arquivos")
        private Integer maxFiles;

        @Field("tamanho_maximo_mb")
        private Integer maxFileSizeMb;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class FormOption {
        @Field("id_opcao")
        private Long id;

        @Field("texto")
        private String text;

        @Field("outra")
        private Boolean other;
    }
}
