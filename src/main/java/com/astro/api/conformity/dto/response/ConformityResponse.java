package com.astro.api.conformity.dto.response;

import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

public record ConformityResponse(
        Long id, Long collaboratorId, String collaboratorName, String collaboratorEmail,
        Integer nrId, String nrTitle, LocalDate expiresAt, String origin, boolean applicable,
        Long eventCompletionId,
        @Schema(allowableValues = {"Vencida", "Válida", "Sem validade"}) String status) { }
