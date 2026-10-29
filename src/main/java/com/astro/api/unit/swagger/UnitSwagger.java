package com.astro.api.unit.swagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.unit.dto.request.UpdateUnitNrsRequest;
import com.astro.api.unit.dto.response.UnitNrOptionResponse;
import com.astro.api.unit.dto.response.UnitNrResponse;
import com.astro.api.unit.dto.response.UnitResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Unidades", description = "Consulta, status e associação de NRs às unidades.")
public interface UnitSwagger {

    @Operation(summary = "Lista unidades do workspace do gestor")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<List<UnitResponse>>> findAll(
             AuthenticatedUser user, HttpServletRequest request);

    @Operation(summary = "Alterna o status da unidade entre ativo e inativo")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<Boolean>> toggleStatus( Long unitId,
             AuthenticatedUser user, HttpServletRequest request);

    @Operation(summary = "Consulta NRs associadas à unidade")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<List<UnitNrResponse>>> findNrs( Long unitId,
             AuthenticatedUser user, HttpServletRequest request);

    @Operation(summary = "Lista NRs disponíveis e indica as associações da unidade")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<List<UnitNrOptionResponse>>> findNrOptions( Long unitId,
             AuthenticatedUser user, HttpServletRequest request);

    @Operation(summary = "Associa ou remove as NRs informadas da unidade")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<Void>> updateNrs( Long unitId,
              UpdateUnitNrsRequest body,
             AuthenticatedUser user, HttpServletRequest request);
}
