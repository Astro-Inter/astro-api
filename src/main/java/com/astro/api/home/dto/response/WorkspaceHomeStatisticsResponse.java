package com.astro.api.home.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WorkspaceHomeStatisticsResponse(
        @JsonProperty("quantidade_unidades") long unitCount,
        @JsonProperty("quantidade_colaboradores") long collaboratorCount,
        @JsonProperty("quantidade_requisitos") long requirementCount,
        @JsonProperty("quantidade_conformes") long compliantCount,
        @JsonProperty("quantidade_proximos_vencimento") long expiringSoonCount,
        @JsonProperty("quantidade_vencidos") long expiredCount,
        @JsonProperty("quantidade_pendentes") long pendingCount,
        @JsonProperty("percentual_conformidade_geral") BigDecimal overallCompliancePercentage,
        @JsonProperty("data_referencia") LocalDate referenceDate
) {
}
