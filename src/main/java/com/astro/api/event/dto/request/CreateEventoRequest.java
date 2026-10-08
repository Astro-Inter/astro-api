package com.astro.api.event.dto.request;

import com.astro.api.event.model.ModoConclusao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateEventoRequest(
        @NotBlank String titulo,
        String descricao,
        Integer nrId,
        String linkExterno,
        @NotNull ModoConclusao modoConclusao,
        boolean evidenciaObrigatoria,
        @NotEmpty List<@Valid CreateTurmaRequest> turmas
) {
}
