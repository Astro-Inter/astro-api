package com.astro.api.unit.swagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.dto.response.UnitResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@Tag(name = "Configurações de unidades", description = "Cadastro e edição de unidades e endereços.")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Nome ou endereço inválido"),
    @ApiResponse(responseCode = "401", description = "Token Firebase ausente ou inválido"),
    @ApiResponse(responseCode = "403", description = "Exige gestor de workspace ativo"),
    @ApiResponse(responseCode = "404", description = "Unidade não encontrada no workspace"),
    @ApiResponse(responseCode = "409", description = "Nome da unidade em conflito")
})
public interface UnitSettingsSwagger {

    @Operation(summary = "Cria uma unidade com endereço no workspace autenticado",
            description = "Consulta o Geoapify no backend para salvar a geolocalização. Se a busca falhar, salva a unidade sem coordenadas.")
    @ApiResponse(responseCode = "201", description = "Recurso criado")
    ResponseEntity<ApiResult<UnitResponse>> create(  UnitRequest body,
             AuthenticatedUser user, HttpServletRequest request);

    @Operation(summary = "Edita nome e endereço da unidade, preservando seus vínculos e status",
            description = "Repete a geocodificação. Se a busca falhar após mudar o endereço, limpa as coordenadas anteriores; se o endereço não mudar, preserva a localização existente.")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<UnitResponse>> update( Long unitId,
              UnitRequest body,  AuthenticatedUser user,
            HttpServletRequest request);
}
