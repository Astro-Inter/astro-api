package com.astro.api.form.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.convert.ValueConverter;
import com.astro.api.form.mapper.FormStatusConverter;
import com.astro.api.form.mapper.FormNumberConverter;

import java.time.Instant;
import java.util.List;

@Document(collection = "forms")
@Getter
@Setter
@NoArgsConstructor
public class Form {
    @Id
    private String id;

    @Field("id_workspace")
    @ValueConverter(FormNumberConverter.class)
    private Long workspaceId;

    @Field("id_gestor")
    @ValueConverter(FormNumberConverter.class)
    private Long managerId;

    @Field("id_gestor_remetente")
    @ValueConverter(FormNumberConverter.class)
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
    @ValueConverter(FormNumberConverter.class)
    private Long unitId;

    @Field("versao")
    @ValueConverter(FormNumberConverter.class)
    private Long version;

    @Field("id_envio")
    private String submissionId;

    @Field("consentimento_localizacao")
    private Boolean locationConsent;

    @Field("localizacoes")
    private List<FormLocationReading> locations;

    @Field("analise")
    private FormReview review;

    @Field("perguntas")
    private List<FormQuestion> questions;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class FormQuestion {
        @Field("id_pergunta")
        @ValueConverter(FormNumberConverter.class)
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
        @ValueConverter(FormNumberConverter.class)
        private Long id;

        @Field("texto")
        private String text;

        @Field("outra")
        private Boolean other;
    }
}
