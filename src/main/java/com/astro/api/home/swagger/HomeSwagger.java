package com.astro.api.home.swagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.home.dto.response.CollaboratorHomeResponse;
import com.astro.api.home.dto.response.ManagerHomeResponse;
import com.astro.api.home.dto.response.WorkspaceHomeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@Tag(name = "Home e indicadores", description = "Indicadores de colaborador, gestor e workspace.")
public interface HomeSwagger {

    @Operation(summary = "Retorna a Home do colaborador", description = "Requer autenticação. status_message pode ser URGENT, PENDENT, ANALYSIS ou OK.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Home retornada com sucesso"),
            @ApiResponse(responseCode = "403", description = "Não autenticado ou sem permissão"),
            @ApiResponse(responseCode = "404", description = "Colaborador inexistente ou inelegível"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível")
    })
    ResponseEntity<ApiResult<CollaboratorHomeResponse>> collaboratorHome(
             AuthenticatedUser authenticatedUser, HttpServletRequest request);

    @Operation(summary = "Consulta nome, foto e indicadores da Home do seu workspace",
            description = "profilePhotoUrl contém a URL temporária da foto da própria conta. Ausência ou falha da foto retorna null sem impedir os indicadores.")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    @ApiResponse(responseCode = "403", description = "Não autenticado ou sem permissão")
    ResponseEntity<ApiResult<WorkspaceHomeResponse>> loadHome(
             AuthenticatedUser user, HttpServletRequest request);

    @Operation(summary = "Retorna a Home do gestor",
            description = "Requer autenticação de gestor. Os indicadores resumem os eventos atuais e as pendências; "
                    + "viewed informa se o item já foi visualizado e days_left informa dias de calendário até o prazo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Home retornada com sucesso"),
            @ApiResponse(responseCode = "403", description = "Não autenticado ou sem permissão"),
            @ApiResponse(responseCode = "404", description = "Gestor inexistente, inativo ou inelegível"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível")
    })
    ResponseEntity<ApiResult<ManagerHomeResponse>> loadManagerHome(
             AuthenticatedUser user, HttpServletRequest request);
}
