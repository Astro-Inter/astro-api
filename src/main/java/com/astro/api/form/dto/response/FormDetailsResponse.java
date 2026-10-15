package com.astro.api.form.dto.response;

import com.astro.api.form.model.FormStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record FormDetailsResponse(
        Long id,
        @JsonProperty("id_workspace") Long workspaceId,
        @JsonProperty("id_gestor_remetente") Long senderManagerId,
        @JsonProperty("id_gestor") Long managerId,
        @JsonProperty("titulo") String title,
        @JsonProperty("descricao") String description,
        Integer nr,
        @JsonProperty("id_unidade") Long unitId,
        @JsonProperty("data_limite") LocalDate dueDate,
        FormStatus status,
        @JsonProperty("criado_em") Instant createdAt,
        @JsonProperty("atualizado_em") Instant updatedAt,
        @JsonProperty("respondido_em") Instant respondedAt,
        @JsonProperty("perguntas") List<QuestionResponse> questions
) {
    public record QuestionResponse(
            @JsonProperty("id_pergunta") Long id,
            @JsonProperty("tipo") String type,
            @JsonProperty("pergunta") String question,
            @JsonProperty("obrigatoria") boolean required,
            @JsonProperty("multipla_escolha") Boolean multiple,
            @JsonProperty("opcoes") List<OptionResponse> options,
            @JsonProperty("maximo_arquivos") Integer maxFiles,
            @JsonProperty("tamanho_maximo_mb") Integer maxFileSizeMb,
            @JsonProperty("resposta") List<Object> answer
    ) {}

    public record OptionResponse(@JsonProperty("id_opcao") Long id,
                                 @JsonProperty("texto") String text,
                                 @JsonProperty("outra") boolean other) {}
}
