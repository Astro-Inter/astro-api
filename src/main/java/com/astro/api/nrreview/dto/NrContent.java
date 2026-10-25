package com.astro.api.nrreview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record NrContent(
        @NotBlank(message = "{nrReview.name.required}")
        @Size(max = 300, message = "{nrReview.name.size}") String nome,
        @NotBlank(message = "{nrReview.description.required}")
        @Size(max = 20000, message = "{nrReview.text.size}") String descricao,
        @NotBlank(message = "{nrReview.objective.required}")
        @Size(max = 20000, message = "{nrReview.text.size}") String objetivo,
        @NotBlank(message = "{nrReview.applicability.required}")
        @Size(max = 20000, message = "{nrReview.text.size}") String aplicabilidade,
        @NotNull(message = "{nrReview.usability.required}")
        @Pattern(regexp = "Colaborador|Empresa", message = "{nrReview.usability.invalid}") String usabilidade,
        @NotNull(message = "{nrReview.revoked.required}") Boolean revogada,
        @NotNull(message = "{nrReview.refresher.required}")
        @Positive(message = "{nrReview.refresher.positive}") Integer tempo_reciclagem_meses
) {}
