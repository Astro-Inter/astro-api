package com.astro.api.user.dto.response;

import java.time.LocalDate;
import java.util.List;

public record UserProfileResponse(
        String nome,
        String cargo,
        String unidade,
        String modalidade,
        String email,
        List<NrProfileResponse> nrs
) {
    public record NrProfileResponse(
            int code,
            LocalDate validade,
            String descricao,
            String objetivo,
            String aplicabilidade
    ) {
    }
}
