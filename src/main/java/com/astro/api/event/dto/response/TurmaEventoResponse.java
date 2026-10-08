package com.astro.api.event.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record TurmaEventoResponse(
        Long id,
        String nome,
        LocalDateTime dataInicial,
        LocalDateTime dataTermino,
        List<ParticipanteEventoResponse> participantes
) {
}
