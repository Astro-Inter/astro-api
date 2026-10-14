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
import java.net.URI;
import jakarta.validation.Valid;
import com.astro.api.conformity.dto.request.CreateConformityRequest;
import com.astro.api.conformity.dto.request.UpdateConformityRequest;
import com.astro.api.conformity.dto.request.ConformityApplicabilityRequest;

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

    @PatchMapping("/{conformityId}/applicability")
    @Operation(summary = "Define explicitamente a aplicabilidade",
            description = "Recebe applicable obrigatório (true/false). Repetir o valor é idempotente. Atualiza somente aplicavel, preservando validade, origem, conclusão e histórico. Reativação exige colaborador ATIVO, NR não revogada e ausência de outro aplicável do par. Desativar o ID pode revelar outro registro aplicável do histórico na próxima listagem.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Aplicabilidade definida"),
            @ApiResponse(responseCode = "404", description = "Conformidade inexistente ou fora do escopo"),
            @ApiResponse(responseCode = "409", description = "Outra conformidade aplicável do par")})
    public ResponseEntity<ApiResult<ConformityResponse>> changeApplicability(@PathVariable Long conformityId,
            @Valid @RequestBody ConformityApplicabilityRequest body,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Aplicabilidade atualizada com sucesso",
                service.changeApplicability(conformityId, body, principal), request.getRequestURI()));
    }

    @PutMapping("/{conformityId}")
    @Operation(summary = "Edita NR e validade de um registro",
            description = "Exige nrId e expiresAt no corpo; expiresAt nulo remove a validade e campo ausente é inválido. Mantém colaborador, ID, origem, aplicabilidade e conclusão. Ao trocar NR de aplicável, rejeita conflitos; a nova NR não pode estar revogada. NR de evento deve corresponder à conclusão vinculada. Não altera conclusao_evento. Retorna pelo ID editado, mesmo fora da representação da view.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Conformidade atualizada"),
            @ApiResponse(responseCode = "404", description = "Conformidade fora do escopo ou NR inexistente"),
            @ApiResponse(responseCode = "409", description = "Outra conformidade aplicável da NR de destino")})
    public ResponseEntity<ApiResult<ConformityResponse>> update(@PathVariable Long conformityId,
            @Valid @RequestBody UpdateConformityRequest body,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Conformidade atualizada com sucesso",
                service.update(conformityId, body, principal), request.getRequestURI()));
    }

    @PostMapping
    @Operation(summary = "Cadastra uma conformidade manual",
            description = "Recebe collaboratorId, nrId e expiresAt opcional/nulo. Exige colaborador ATIVO no escopo e NR existente não revogada. Cria MANUAL, aplicável, sem conclusão vinculada. Rejeita qualquer registro aplicável do par; mantém os anteriores não aplicáveis. Usa transação com bloqueio de escrita.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Conformidade criada"),
            @ApiResponse(responseCode = "404", description = "Colaborador fora do escopo ou NR inexistente"),
            @ApiResponse(responseCode = "409", description = "Registro aplicável já existente")})
    public ResponseEntity<ApiResult<ConformityResponse>> create(@Valid @RequestBody CreateConformityRequest body,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        var result = service.create(body, principal);
        return ResponseEntity.created(URI.create("/conformities/" + result.id()))
                .body(ApiResult.success("Conformidade criada com sucesso", result, request.getRequestURI()));
    }

    @GetMapping("/{conformityId}")
    @Operation(summary = "Consulta um registro de conformidade pelo ID",
            description = "Consulta a tabela e seus relacionamentos no escopo autorizado. Retorna registros não aplicáveis e históricos que já não representam o par na view. Preserva origem e conclusão vinculada.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Detalhes da conformidade"),
            @ApiResponse(responseCode = "404", description = "Registro inexistente ou fora do escopo")})
    public ResponseEntity<ApiResult<ConformityResponse>> findById(@PathVariable Long conformityId,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Conformidade retornada com sucesso",
                service.findById(conformityId, principal), request.getRequestURI()));
    }

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
