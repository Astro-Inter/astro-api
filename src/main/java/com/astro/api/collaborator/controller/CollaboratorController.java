package com.astro.api.collaborator.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.collaborator.dto.response.CollaboratorSummaryResponse;
import com.astro.api.collaborator.dto.response.CollaboratorDetailsResponse;
import com.astro.api.collaborator.service.CollaboratorService;
import com.astro.api.common.response.ApiResult;
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
@RequestMapping("/collaborators")
@PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Parâmetro ou corpo inválido"),
        @ApiResponse(responseCode = "401", description = "Autenticação ausente ou inválida"),
        @ApiResponse(responseCode = "403", description = "Sem permissão de gestor ativo"),
        @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível")
})
public class CollaboratorController {
    private final CollaboratorService service;

    public CollaboratorController(CollaboratorService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista colaboradores do escopo do gestor",
            description = "Lista completa, ordenada por nome e ID, com os três status. Não expõe CPF nem dados de autenticação. Não exige filtros ou paginação.")
    @ApiResponse(responseCode = "200", description = "Lista de colaboradores, possivelmente vazia")
    public ResponseEntity<ApiResult<List<CollaboratorSummaryResponse>>> findAll(
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Colaboradores retornados com sucesso",
                service.findAll(user), request.getRequestURI()));
    }

    @GetMapping("/{collaboratorId}")
    @Operation(summary = "Consulta detalhes do colaborador para informações e edição",
            description = "Inclui CPF e modalidade; restringe a consulta ao workspace e às unidades autorizadas. Não expõe dados de autenticação.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Detalhes do colaborador"),
            @ApiResponse(responseCode = "404", description = "Colaborador inexistente ou fora do escopo")})
    public ResponseEntity<ApiResult<CollaboratorDetailsResponse>> findById(
            @PathVariable Long collaboratorId, @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Colaborador retornado com sucesso",
                service.findById(collaboratorId, user), request.getRequestURI()));
    }
}
