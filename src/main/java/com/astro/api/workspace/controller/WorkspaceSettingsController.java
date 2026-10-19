package com.astro.api.workspace.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.workspace.dto.request.UpdateWorkspaceRequest;
import com.astro.api.workspace.dto.request.WorkspaceImportRequest;
import com.astro.api.workspace.dto.response.WorkspaceImportResponse;
import com.astro.api.workspace.dto.response.WorkspaceResponse;
import com.astro.api.workspace.service.WorkspaceImportService;
import com.astro.api.workspace.service.WorkspaceSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/workspace")
@PreAuthorize("hasRole('GESTOR_WORKSPACE')")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Token Firebase ausente ou inválido"),
    @ApiResponse(responseCode = "403", description = "Exige gestor de workspace ativo"),
    @ApiResponse(responseCode = "409", description = "Conflito de identidade ou unicidade")
})
public class WorkspaceSettingsController {
    private final WorkspaceSettingsService workspaceSettingsService;
    private final WorkspaceImportService workspaceImportService;

    public WorkspaceSettingsController(WorkspaceSettingsService workspaceSettingsService, WorkspaceImportService workspaceImportService) {
        this.workspaceSettingsService = workspaceSettingsService;
        this.workspaceImportService = workspaceImportService;
    }

    @GetMapping
    @Operation(summary = "Consulta nome e CNPJ do workspace autenticado")
    public ResponseEntity<ApiResult<WorkspaceResponse>> find(@AuthenticationPrincipal AuthenticatedUser user,
                                                            HttpServletRequest request) {
        WorkspaceResponse workspaceResponse = workspaceSettingsService.find(user);
        ApiResult<WorkspaceResponse> result = ApiResult.success(
                "Workspace retornado com sucesso", workspaceResponse, request.getRequestURI());
        return ResponseEntity.ok(result);
    }

    @PutMapping
    @Operation(summary = "Atualiza nome e CNPJ do workspace autenticado")
    public ResponseEntity<ApiResult<WorkspaceResponse>> update(@Valid @RequestBody UpdateWorkspaceRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        WorkspaceResponse workspaceResponse = workspaceSettingsService.update(body, user);
        ApiResult<WorkspaceResponse> result = ApiResult.success(
                "Workspace atualizado com sucesso", workspaceResponse, request.getRequestURI());
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Sincroniza colaboradores, cargos e novas unidades por Excel",
            description = "Identifica colaboradores por CPF. Cria novos, atualiza alterações e ignora os demais. "
                    + "Novas sedes exigem endereço na parte JSON data.units. Limite: 10 MB.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            encoding = @Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE)))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resumo de criados, atualizados e ignorados"),
        @ApiResponse(responseCode = "422", description = "Erros por linha/campo; nenhum dado gravado"),
        @ApiResponse(responseCode = "413", description = "Arquivo acima de 10 MB")
    })
    public ResponseEntity<ApiResult<WorkspaceImportResponse>> importFile(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "data", required = false) @Valid WorkspaceImportRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        WorkspaceImportRequest importRequest = body == null ? new WorkspaceImportRequest(List.of()) : body;
        WorkspaceImportResponse importResponse = workspaceImportService.importFile(file, importRequest, user);
        ApiResult<WorkspaceImportResponse> result = ApiResult.success(
                "Planilha sincronizada com sucesso", importResponse, request.getRequestURI());
        return ResponseEntity.ok(result);
    }
}
