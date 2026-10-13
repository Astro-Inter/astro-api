package com.astro.api.manager.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.manager.dto.response.ManagerSummaryResponse;
import com.astro.api.manager.dto.response.ManagerDetailsResponse;
import com.astro.api.manager.dto.request.PromoteManagerRequest;
import jakarta.validation.Valid;
import java.net.URI;
import com.astro.api.manager.service.ManagerService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/managers")
@PreAuthorize("hasRole('GESTOR_WORKSPACE')")
public class ManagerController {
    private final ManagerService service;

    public ManagerController(ManagerService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Lista gestores de todas as unidades do próprio workspace")
    public ResponseEntity<ApiResult<List<ManagerSummaryResponse>>> findAll(
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Gestores retornados com sucesso",
                service.findAll(principal), request.getRequestURI()));
    }

    @GetMapping("/{managerId}")
    @Operation(summary = "Consulta os detalhes de um gestor do próprio workspace")
    public ResponseEntity<ApiResult<ManagerDetailsResponse>> findById(@PathVariable("managerId") Long managerId,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Gestor retornado com sucesso",
                service.findById(managerId, principal), request.getRequestURI()));
    }

    @PostMapping
    @Operation(summary = "Promove um colaborador ativo a gestor, preservando a conta existente")
    public ResponseEntity<ApiResult<ManagerDetailsResponse>> promote(@Valid @RequestBody PromoteManagerRequest body,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        var manager = service.promote(body, principal);
        return ResponseEntity.created(URI.create("/managers/" + manager.id())).body(ApiResult.success(
                "Colaborador promovido a gestor com sucesso", manager, request.getRequestURI()));
    }
}
