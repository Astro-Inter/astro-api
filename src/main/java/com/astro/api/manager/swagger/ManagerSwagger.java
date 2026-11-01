package com.astro.api.manager.swagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.manager.dto.request.ManagerStatusRequest;
import com.astro.api.manager.dto.request.PromoteManagerRequest;
import com.astro.api.manager.dto.request.UpdateManagerRequest;
import com.astro.api.manager.dto.response.ManagerDetailsResponse;
import com.astro.api.manager.dto.response.ManagerSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Gestores", description = "Gestão de contas de gestores do workspace.")
public interface ManagerSwagger {

    @Operation(summary = "Lista gestores de todas as unidades do próprio workspace")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<List<ManagerSummaryResponse>>> findAll(
             AuthenticatedUser principal, HttpServletRequest request);

    @Operation(summary = "Consulta os detalhes de um gestor do próprio workspace")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<ManagerDetailsResponse>> findById( Long managerId,
             AuthenticatedUser principal, HttpServletRequest request);

    @Operation(summary = "Promove um colaborador ativo a gestor, preservando a conta existente")
    @ApiResponse(responseCode = "201", description = "Recurso criado")
    ResponseEntity<ApiResult<ManagerDetailsResponse>> promote(  PromoteManagerRequest body,
             AuthenticatedUser principal, HttpServletRequest request);

    @Operation(summary = "Despromove um gestor do próprio workspace a colaborador",
            description = "Exige gestor de workspace ativo. Altera somente o perfil GESTOR para COLABORADOR, "
                    + "preservando status, conta, vínculos e histórico. Não recebe corpo. "
                    + "Retorna os dados administrativos da conta preservada.")
    @ApiResponse(responseCode = "200", description = "Gestor despromovido a colaborador com sucesso")
    @ApiResponse(responseCode = "400", description = "Identificador inválido")
    @ApiResponse(responseCode = "403", description = "Autenticação ou permissão insuficiente")
    @ApiResponse(responseCode = "404", description = "Gestor inexistente, outro perfil ou fora do workspace")
    ResponseEntity<ApiResult<ManagerDetailsResponse>> demote(Long managerId,
            AuthenticatedUser principal, HttpServletRequest request);

    @Operation(summary = "Substitui todos os campos editáveis de um gestor do próprio workspace")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<ManagerDetailsResponse>> update( Long managerId,
              UpdateManagerRequest body,  AuthenticatedUser principal,
            HttpServletRequest request);

    @Operation(summary = "Ativa ou desativa explicitamente toda a conta de um gestor")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<ManagerDetailsResponse>> changeStatus( Long managerId,
              ManagerStatusRequest body,  AuthenticatedUser principal,
            HttpServletRequest request);
}
