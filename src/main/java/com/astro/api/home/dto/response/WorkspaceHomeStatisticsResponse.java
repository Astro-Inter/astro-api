package com.astro.api.home.dto.response;


import java.math.BigDecimal;
import java.time.LocalDate;

public record WorkspaceHomeStatisticsResponse(
        long unitCount,
        long collaboratorCount,
        long requirementCount,
        long compliantCount,
        long expiringSoonCount,
        long expiredCount,
        long pendingCount,
        BigDecimal overallCompliancePercentage,
        LocalDate referenceDate
) {
}
