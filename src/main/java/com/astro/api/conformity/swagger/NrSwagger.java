package com.astro.api.conformity.swagger;

import com.astro.api.common.response.ApiResult;
import com.astro.api.conformity.dto.response.NrResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Normas regulamentadoras", description = "Consulta do catálogo de NRs.")
public interface NrSwagger {

    @Operation(summary = "Lista os códigos de NR disponíveis para seleção")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "NRs retornadas com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado")
    })
    ResponseEntity<ApiResult<List<NrResponse>>> findAll(HttpServletRequest request);
}
