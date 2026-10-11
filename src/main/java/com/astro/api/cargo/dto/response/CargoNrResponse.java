package com.astro.api.cargo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CargoNrResponse(@JsonProperty("codigo_nr") Integer codigoNr, String descricao) {
}
