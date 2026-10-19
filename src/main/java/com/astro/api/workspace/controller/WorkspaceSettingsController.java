package com.astro.api.workspace.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.workspace.dto.response.WorkspaceResponse;
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

    public WorkspaceSettingsController(WorkspaceSettingsService workspaceSettingsService) {
        this.workspaceSettingsService = workspaceSettingsService;
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

}
