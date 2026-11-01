package com.astro.api.manager.controller;

import com.astro.api.manager.swagger.ManagerSwagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.manager.dto.response.ManagerSummaryResponse;
import com.astro.api.manager.dto.response.ManagerDetailsResponse;
import com.astro.api.manager.dto.request.PromoteManagerRequest;
import com.astro.api.manager.dto.request.UpdateManagerRequest;
import com.astro.api.manager.dto.request.ManagerStatusRequest;
import jakarta.validation.Valid;
import java.net.URI;
import com.astro.api.manager.service.ManagerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/managers")
@PreAuthorize("hasRole('GESTOR_WORKSPACE')")
public class ManagerController implements ManagerSwagger {
    private final ManagerService service;

    public ManagerController(ManagerService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<ApiResult<List<ManagerSummaryResponse>>> findAll(
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Gestores retornados com sucesso",
                service.findAll(principal), request.getRequestURI()));
    }

    @GetMapping("/{managerId}")
    public ResponseEntity<ApiResult<ManagerDetailsResponse>> findById(@PathVariable("managerId") Long managerId,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Gestor retornado com sucesso",
                service.findById(managerId, principal), request.getRequestURI()));
    }

    @PostMapping
    public ResponseEntity<ApiResult<ManagerDetailsResponse>> promote(@Valid @RequestBody PromoteManagerRequest body,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        ManagerDetailsResponse manager = service.promote(body, principal);
        return ResponseEntity.created(URI.create("/managers/" + manager.id())).body(ApiResult.success(
                "Colaborador promovido a gestor com sucesso", manager, request.getRequestURI()));
    }

    @PatchMapping("/{managerId}/demote")
    public ResponseEntity<ApiResult<ManagerDetailsResponse>> demote(@PathVariable("managerId") Long managerId,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Gestor despromovido a colaborador com sucesso",
                service.demote(managerId, principal), request.getRequestURI()));
    }

    @PutMapping("/{managerId}")
    public ResponseEntity<ApiResult<ManagerDetailsResponse>> update(@PathVariable("managerId") Long managerId,
            @Valid @RequestBody UpdateManagerRequest body, @AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Gestor atualizado com sucesso",
                service.update(managerId, body, principal), request.getRequestURI()));
    }

    @PatchMapping("/{managerId}/status")
    public ResponseEntity<ApiResult<ManagerDetailsResponse>> changeStatus(@PathVariable("managerId") Long managerId,
            @Valid @RequestBody ManagerStatusRequest body, @AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Status do gestor alterado com sucesso",
                service.changeStatus(managerId, body, principal), request.getRequestURI()));
    }
}
