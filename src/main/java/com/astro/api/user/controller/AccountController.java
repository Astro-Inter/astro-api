package com.astro.api.user.controller;

import com.astro.api.user.swagger.AccountSwagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.user.dto.response.AccountSummaryResponse;
import com.astro.api.user.dto.response.AccountDetailsResponse;
import com.astro.api.user.dto.request.UpdateAccountDetailsRequest;
import com.astro.api.user.service.AccountService;
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
public class AccountController implements AccountSwagger {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/user/me/account-summary")
    public ResponseEntity<ApiResult<AccountSummaryResponse>> summary(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Resumo da conta retornado com sucesso",
                accountService.findSummary(authenticatedUser), request.getRequestURI()));
    }

    @GetMapping("/user/me/account-details")
    public ResponseEntity<ApiResult<AccountDetailsResponse>> details(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Detalhes da conta retornados com sucesso",
                accountService.findDetails(authenticatedUser), request.getRequestURI()));
    }

    @PutMapping("/user/me/account-details")
    public ResponseEntity<ApiResult<AccountDetailsResponse>> update(@Valid @RequestBody UpdateAccountDetailsRequest updateAccountDetailsRequest,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Conta atualizada com sucesso",
                accountService.update(updateAccountDetailsRequest, authenticatedUser), request.getRequestURI()));
    }
}
