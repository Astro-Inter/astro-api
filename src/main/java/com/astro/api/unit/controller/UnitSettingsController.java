package com.astro.api.unit.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.dto.response.UnitResponse;
import com.astro.api.unit.service.UnitSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/units")
@PreAuthorize("hasRole('GESTOR_WORKSPACE')")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Nome ou endereço inválido"),
    @ApiResponse(responseCode = "401", description = "Token Firebase ausente ou inválido"),
    @ApiResponse(responseCode = "403", description = "Exige gestor de workspace ativo"),
    @ApiResponse(responseCode = "404", description = "Unidade não encontrada no workspace"),
    @ApiResponse(responseCode = "409", description = "Nome da unidade em conflito")
})
public class UnitSettingsController {
    private final UnitSettingsService unitSettingsService;

    public UnitSettingsController(UnitSettingsService unitSettingsService) {
        this.unitSettingsService = unitSettingsService;
    }

    @PostMapping
    @Operation(summary = "Cria uma unidade com endereço no workspace autenticado",
            description = "Consulta o Geoapify no backend para salvar a geolocalização. Se a busca falhar, salva a unidade sem coordenadas.")
    public ResponseEntity<ApiResult<UnitResponse>> create(@Valid @RequestBody UnitRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        UnitResponse response = unitSettingsService.create(body, user);
        URI location = URI.create("/units/" + response.unitId());
        ApiResult<UnitResponse> result = ApiResult.success(
                "Unidade cadastrada com sucesso", response, request.getRequestURI());
        return ResponseEntity.created(location).body(result);
    }

    @PutMapping("/{unitId}")
    @Operation(summary = "Edita nome e endereço da unidade, preservando seus vínculos e status",
            description = "Repete a geocodificação. Se a busca falhar após mudar o endereço, limpa as coordenadas anteriores; se o endereço não mudar, preserva a localização existente.")
    public ResponseEntity<ApiResult<UnitResponse>> update(@PathVariable Long unitId,
            @Valid @RequestBody UnitRequest body, @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) {
        UnitResponse response = unitSettingsService.update(unitId, body, user);
        ApiResult<UnitResponse> result = ApiResult.success(
                "Unidade atualizada com sucesso", response, request.getRequestURI());
        return ResponseEntity.ok(result);
    }
}
