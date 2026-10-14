package com.astro.api.event.dto.response;

import com.astro.api.event.model.CompletionMode;
import com.astro.api.event.model.EventStatus;

import java.time.Instant;
import java.util.List;

public record EventResponse(
        Long id,
        String title,
        String description,
        Integer nrId,
        String externalLink,
        CompletionMode completionMode,
        boolean mandatoryEvidence,
        EventStatus status,
        Instant cancellationDate,
        String cancellationReason,
        Long managerId,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Criador de evento ativo; pode editar ou adicionar turmas futuras")
        boolean canEdit,
        boolean canCancel,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Metadados editáveis somente antes de qualquer turma iniciar")
        boolean detailsEditable,
        List<EventClassResponse> eventClasses
) {
}
