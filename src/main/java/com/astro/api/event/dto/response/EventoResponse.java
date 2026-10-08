package com.astro.api.event.dto.response;

import com.astro.api.event.model.ModoConclusao;
import com.astro.api.event.model.StatusEvento;

import java.time.Instant;
import java.util.List;

public record EventoResponse(
        Long id,
        String titulo,
        String descricao,
        Integer nrId,
        String linkExterno,
        ModoConclusao modoConclusao,
        boolean evidenciaObrigatoria,
        StatusEvento status,
        Instant dataCancelamento,
        String motivoCancelamento,
        List<TurmaEventoResponse> turmas
) {
}
