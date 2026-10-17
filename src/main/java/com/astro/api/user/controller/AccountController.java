package com.astro.api.user.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.user.dto.response.AccountSummaryResponse;
import com.astro.api.user.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
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
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Resumo da conta retornado com sucesso",
                accounts.findSummary(principal), request.getRequestURI()));
    }
}
