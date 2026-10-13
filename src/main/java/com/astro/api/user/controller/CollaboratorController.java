package com.astro.api.user.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.user.dto.response.CollaboratorSummaryResponse;
import com.astro.api.user.dto.response.CollaboratorDetailsResponse;
import com.astro.api.user.dto.request.CreateCollaboratorRequest;
import com.astro.api.user.dto.request.UpdateCollaboratorRequest;
import com.astro.api.user.dto.request.CollaboratorStatusRequest;
import jakarta.validation.Valid;
import java.net.URI;
import com.astro.api.user.service.CollaboratorService;
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
            description = "Lista completa, ordenada por nome e ID, com os três status, URL de foto de perfil e iniciais para fallback. Não expõe CPF nem dados de autenticação. Não exige filtros ou paginação.")
    @ApiResponse(responseCode = "200", description = "Lista de colaboradores, possivelmente vazia")
    public ResponseEntity<ApiResult<List<CollaboratorSummaryResponse>>> findAll(
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Colaboradores retornados com sucesso",
                service.findAll(user), request.getRequestURI()));
    }

    @GetMapping("/{collaboratorId}")
    @Operation(summary = "Consulta detalhes do colaborador para informações e edição",
            description = "Inclui CPF, modalidade, URL de foto de perfil e iniciais; restringe a consulta ao workspace e às unidades autorizadas. Não expõe dados de autenticação.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Detalhes do colaborador"),
            @ApiResponse(responseCode = "404", description = "Colaborador inexistente ou fora do escopo")})
    public ResponseEntity<ApiResult<CollaboratorDetailsResponse>> findById(
            @PathVariable Long collaboratorId, @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Colaborador retornado com sucesso",
                service.findById(collaboratorId, user), request.getRequestURI()));
    }

    @PostMapping
    @Operation(summary = "Pré-cadastra um colaborador",
            description = "Exige nome, CPF válido, e-mail, roleId, unitId e modalidade. Normaliza CPF e e-mail, valida vínculos ativos e cria COLABORADOR/PRE_CADASTRADO sem senha. Reutiliza a fila de conclusão do cadastro.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Colaborador pré-cadastrado"),
            @ApiResponse(responseCode = "404", description = "Cargo ou unidade inexistente no workspace"),
            @ApiResponse(responseCode = "409", description = "CPF ou e-mail já cadastrado")})
    public ResponseEntity<ApiResult<CollaboratorDetailsResponse>> create(
            @Valid @RequestBody CreateCollaboratorRequest body, @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) {
        var response = service.create(body, user);
        return ResponseEntity.created(URI.create("/collaborators/" + response.id()))
                .body(ApiResult.success("Colaborador pré-cadastrado com sucesso", response, request.getRequestURI()));
    }

    @PutMapping("/{collaboratorId}")
    @Operation(summary = "Edita todos os campos administrativos do colaborador",
            description = "Exige todos os campos editáveis não nulos, incluindo status. Mantém vínculos inativos quando não alterados. Preserva ID, tipo, criado_em e Firebase UID. Mudanças de e-mail vinculadas ao Firebase usam compensação em caso de falha.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Colaborador atualizado"),
            @ApiResponse(responseCode = "404", description = "Colaborador, cargo ou unidade inexistente no escopo"),
            @ApiResponse(responseCode = "409", description = "CPF ou e-mail em conflito"),
            @ApiResponse(responseCode = "500", description = "Falha de sincronização com a autenticação")})
    public ResponseEntity<ApiResult<CollaboratorDetailsResponse>> update(@PathVariable Long collaboratorId,
            @Valid @RequestBody UpdateCollaboratorRequest body, @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Colaborador atualizado com sucesso",
                service.update(collaboratorId, body, user), request.getRequestURI()));
    }

    @PatchMapping("/{collaboratorId}/status")
    @Operation(summary = "Altera explicitamente o status do colaborador",
            description = "Aceita ATIVO ou DESATIVADO. Repetir o status atual é idempotente. Ativação exige cadastro concluído e cargo/unidade ativos; não conclui pré-cadastro. A desativação impede operações protegidas na próxima requisição.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Status aplicado, com detalhes do colaborador"),
            @ApiResponse(responseCode = "404", description = "Colaborador inexistente ou fora do escopo")})
    public ResponseEntity<ApiResult<CollaboratorDetailsResponse>> changeStatus(@PathVariable Long collaboratorId,
            @Valid @RequestBody CollaboratorStatusRequest body, @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Status do colaborador atualizado com sucesso",
                service.changeStatus(collaboratorId, body, user), request.getRequestURI()));
    }
}
