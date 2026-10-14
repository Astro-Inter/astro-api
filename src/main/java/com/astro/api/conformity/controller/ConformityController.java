package com.astro.api.conformity.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.conformity.dto.response.ConformityResponse;
import com.astro.api.conformity.service.ConformityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/conformities")
@PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
@ApiResponses({@ApiResponse(responseCode = "400", description = "Requisição inválida"),
        @ApiResponse(responseCode = "401", description = "Autenticação ausente ou inválida"),
        @ApiResponse(responseCode = "403", description = "Gestor ativo necessário"),
        @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível")})
public class ConformityController {
    private final ConformityService service;
    public ConformityController(ConformityService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Lista conformidades aplicáveis no escopo autorizado",
            description = "Consulta vw_conformidades: uma linha por colaborador/NR, priorizando aplicáveis, maior validade (nulos por último) e maior ID. Inclui histórico de colaboradores inativos e NRs revogadas. Lista completa ordenada por nome, colaborador, NR e ID. Status segue CURRENT_DATE da sessão PostgreSQL; data nula significa Sem validade.")
    @ApiResponse(responseCode = "200", description = "Lista de conformidades, possivelmente vazia")
    public ResponseEntity<ApiResult<List<ConformityResponse>>> findAll(
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Conformidades retornadas com sucesso",
                service.findAll(principal), request.getRequestURI()));
    }
}
