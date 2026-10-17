package com.astro.api.user.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.user.dto.response.AccountSummaryResponse;
import com.astro.api.user.dto.response.AccountDetailsResponse;
import com.astro.api.user.dto.request.UpdateAccountDetailsRequest;
import com.astro.api.user.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/user/me/account-summary")
    @Operation(summary = "Consulta o resumo da própria conta", description = "Identifica a conta exclusivamente pelo token Firebase. Retorna somente nome, email e profilePhotoUrl, sem NRs ou vínculos administrativos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumo no envelope ApiResult; foto ausente retorna null"),
            @ApiResponse(responseCode = "403", description = "Token ausente/inválido ou perfil sem acesso, conforme a segurança existente"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado (ApiResult de erro)"),
            @ApiResponse(responseCode = "500", description = "Erro interno ou falha ao gerar a URL da foto (ApiResult de erro)"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível (ApiResult de erro)")
    })
    public ResponseEntity<ApiResult<AccountSummaryResponse>> summary(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Resumo da conta retornado com sucesso",
                accountService.findSummary(authenticatedUser), request.getRequestURI()));
    }

    @GetMapping("/user/me/account-details")
    @Operation(summary = "Consulta os detalhes da própria conta", description = "Retorna os nove campos da conta web, com IDs e nomes de cargo/unidade. CPF, modalidade, cargo e foto ausentes permanecem null. Não recebe senha nem exige confirmação de senha no frontend como autorização.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhes no envelope ApiResult"),
            @ApiResponse(responseCode = "403", description = "Token ausente/inválido, perfil sem acesso ou vínculo fora do workspace"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado (ApiResult de erro)"),
            @ApiResponse(responseCode = "500", description = "Erro interno ou falha ao gerar foto (ApiResult de erro)"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível (ApiResult de erro)")
    })
    public ResponseEntity<ApiResult<AccountDetailsResponse>> details(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Detalhes da conta retornados com sucesso",
                accountService.findDetails(authenticatedUser), request.getRequestURI()));
    }

    @PutMapping("/user/me/account-details")
    @Operation(summary = "Substitui os seis campos editáveis da própria conta", description = "Exige name, cpf, email, roleId, unitId e workModel, sem nulos ou campos extras. GESTOR mantém cargo/unidade; GESTOR_WORKSPACE pode escolher vínculos ativos no próprio workspace. Preserva vínculos inativos atuais. Não recebe senha ou foto. E-mail alterado é sincronizado com Firebase e marcado como não verificado; confirmação deve ocorrer no fluxo Firebase do cliente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhes atualizados no envelope ApiResult, após commit e sincronização de e-mail"),
            @ApiResponse(responseCode = "400", description = "Campos omitidos, nulos, desconhecidos, formatos inválidos ou novo vínculo inativo (ApiResult de erro)"),
            @ApiResponse(responseCode = "403", description = "Token ausente/inválido, perfil sem acesso ou mudança de vínculo sem permissão"),
            @ApiResponse(responseCode = "404", description = "Usuário, cargo ou unidade não encontrados no workspace (ApiResult de erro)"),
            @ApiResponse(responseCode = "409", description = "CPF/e-mail duplicado ou identidade Firebase divergente (ApiResult de erro)"),
            @ApiResponse(responseCode = "500", description = "Falha interna/Firebase; sem sucesso parcial, com compensação ou contenção (ApiResult de erro)"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível (ApiResult de erro)")
    })
    public ResponseEntity<ApiResult<AccountDetailsResponse>> update(@Valid @RequestBody UpdateAccountDetailsRequest updateAccountDetailsRequest,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Conta atualizada com sucesso",
                accountService.update(updateAccountDetailsRequest, authenticatedUser), request.getRequestURI()));
    }
}
