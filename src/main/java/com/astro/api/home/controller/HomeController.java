package com.astro.api.home.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.home.dto.response.HomeResponse;
import com.astro.api.home.dto.response.ManagerHomeResponse;
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
public class HomeController {
    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping("/home-workspace")
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    @Operation(summary = "Consulta nome do gestor e indicadores da Home do seu workspace")
    public ResponseEntity<ApiResult<HomeResponse>> loadHome(
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Dados da Home retornados com sucesso",
                homeService.loadHome(user), request.getRequestURI()));
    }

    @GetMapping("/home/manager")
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    @Operation(summary = "Retorna a Home do gestor",
            description = "Requer autenticação de gestor. Os indicadores resumem os eventos atuais e as pendências; "
                    + "viewed informa se o item já foi visualizado e days_left informa dias de calendário até o prazo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Home retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado"),
            @ApiResponse(responseCode = "404", description = "Gestor inexistente, inativo ou inelegível"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível")
    })
    public ResponseEntity<ApiResult<ManagerHomeResponse>> loadManagerHome(
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        ManagerHomeResponse response = homeService.loadManagerHome(user);
        return ResponseEntity.ok(ApiResult.success("Home do gestor retornada com sucesso", response,
                request.getRequestURI()));
    }
}
