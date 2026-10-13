package com.astro.api.home.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.home.dto.response.WorkspaceHomeResponse;
import com.astro.api.home.dto.response.CollaboratorHomeResponse;
import com.astro.api.home.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/home")
public class HomeController {
    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping("/collaborator")
    @PreAuthorize("hasAnyRole('COLABORADOR', 'GESTOR', 'GESTOR_WORKSPACE')")
    @Operation(summary = "Retorna a Home do colaborador", description = "Requer autenticação. status_message pode ser URGENT, PENDENT, ANALYSIS ou OK.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Home retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado"),
            @ApiResponse(responseCode = "404", description = "Colaborador inexistente ou inelegível"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível")
    })
    public ResponseEntity<ApiResult<CollaboratorHomeResponse>> collaboratorHome(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, HttpServletRequest request) {
        CollaboratorHomeResponse response = homeService.getCollaboratorHome(authenticatedUser.firebaseUid());
        return ResponseEntity.ok(ApiResult.success("Home do colaborador retornada com sucesso", response,
                request.getRequestURI()));
    }

    @GetMapping("/workspace")
    @Operation(summary = "Consulta nome do gestor e indicadores da Home do seu workspace")
    public ResponseEntity<ApiResult<WorkspaceHomeResponse>> loadHome(
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Dados da Home retornados com sucesso",
                homeService.loadHome(user), request.getRequestURI()));
    }
}
