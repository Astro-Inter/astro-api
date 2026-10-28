package com.astro.api.conformity.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.conformity.dto.request.*;
import com.astro.api.conformity.dto.response.UnitConformityResponse;
import com.astro.api.conformity.service.UnitConformityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "unit-conformity-controller")
@RestController
@RequestMapping("/conformities/units")
@PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
@ApiResponses({@ApiResponse(responseCode="400", description="Dados inválidos"),
        @ApiResponse(responseCode="401", description="Autenticação necessária"),
        @ApiResponse(responseCode="403", description="Gestor ativo necessário"),
        @ApiResponse(responseCode="404", description="Recurso fora do escopo ou inexistente"),
        @ApiResponse(responseCode="409", description="Conflito de conformidade"),
        @ApiResponse(responseCode="503", description="Dados indisponíveis")})
public class UnitConformityController {
    private final UnitConformityService service;
    public UnitConformityController(UnitConformityService service) { this.service = service; }
    @GetMapping
    @Operation(summary="Lista conformidades aplicáveis de unidades", description="Um representante por unidade/NR, restrito ao workspace e às unidades autorizadas, ordenado por nome. Inclui unidades inativas e NRs históricas.")
    public ResponseEntity<ApiResult<List<UnitConformityResponse>>> list(@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Conformidades retornadas", service.findAll(user), request.getRequestURI()));
    }
    @GetMapping("/{id}")
    @Operation(summary="Consulta uma conformidade de unidade", description="Permite consultar o histórico autorizado, inclusive registros não aplicáveis.")
    public ResponseEntity<ApiResult<UnitConformityResponse>> details(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Conformidade retornada", service.findById(id, user), request.getRequestURI()));
    }
    @PostMapping
    @Operation(summary="Cria conformidade manual de unidade", description="Exige unidade ativa autorizada e NR vigente; rejeita outro aplicável do par. Mantém o histórico.")
    @ApiResponse(responseCode="201", description="Conformidade criada")
    public ResponseEntity<ApiResult<UnitConformityResponse>> create(@Valid @RequestBody CreateUnitConformityRequest body, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        UnitConformityResponse saved = service.create(body, user);
        return ResponseEntity.created(URI.create("/conformities/units/" + saved.id())).body(ApiResult.success("Conformidade criada", saved, request.getRequestURI()));
    }
    @PutMapping("/{id}")
    @Operation(summary="Edita NR e validade da conformidade de unidade", description="Exige expiresAt presente, aceitando null. Preserva destinatário e evidência; não permite trocar a NR vinculada a formulário.")
    public ResponseEntity<ApiResult<UnitConformityResponse>> update(@PathVariable Long id, @Valid @RequestBody UpdateConformityRequest body, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Conformidade atualizada", service.update(id, body, user), request.getRequestURI()));
    }
    @PatchMapping("/{id}/applicability")
    @Operation(summary="Define aplicabilidade da conformidade de unidade", description="Idempotente por ID. Reativar exige unidade ativa, NR vigente e ausência de outro aplicável. Não exclui histórico.")
    public ResponseEntity<ApiResult<UnitConformityResponse>> applicability(@PathVariable Long id, @Valid @RequestBody ConformityApplicabilityRequest body, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Aplicabilidade atualizada", service.changeApplicability(id, body, user), request.getRequestURI()));
    }
}
