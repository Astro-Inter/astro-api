package com.astro.api.nrreview.dto;

import jakarta.validation.constraints.*;
import org.bson.Document;

/** Only human-editable fields. Source dates and IDs come from the stored snapshot. */
public record NrContent(
        @NotBlank @Size(max = 300) String nome,
        @NotBlank @Size(max = 20000) String descricao,
        @NotBlank @Size(max = 20000) String objetivo,
        @NotBlank @Size(max = 20000) String aplicabilidade,
        @NotNull @Pattern(regexp = "Colaborador|Empresa") String usabilidade,
        @NotNull Boolean revogada,
        @NotNull @Positive Integer tempo_reciclagem_meses
) {
    public Document applyTo(Document original) {
        return new Document(original).append("nome", nome).append("descricao", descricao)
                .append("objetivo", objetivo).append("aplicabilidade", aplicabilidade)
                .append("usabilidade", usabilidade).append("revogada", revogada)
                .append("tempo_reciclagem_meses", tempo_reciclagem_meses);
    }
}
