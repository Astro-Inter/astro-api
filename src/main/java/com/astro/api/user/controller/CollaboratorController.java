package com.astro.api.user.controller;

import com.astro.api.user.swagger.CollaboratorSwagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.user.dto.response.CollaboratorSummaryResponse;
import com.astro.api.user.dto.response.CollaboratorDetailsResponse;
import com.astro.api.user.dto.response.CollaboratorEventsResponse;
import com.astro.api.user.dto.response.CollaboratorPendingCountResponse;
import com.astro.api.user.dto.response.CollaboratorProfileResponse;
import com.astro.api.user.dto.request.CreateCollaboratorRequest;
import com.astro.api.user.dto.request.UpdateCollaboratorRequest;
import com.astro.api.user.dto.request.CollaboratorStatusRequest;
import jakarta.validation.Valid;
import java.net.URI;
import com.astro.api.user.service.CollaboratorService;
import com.astro.api.common.response.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/collaborators")
@PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
public class CollaboratorController implements CollaboratorSwagger {
    private final CollaboratorService service;

    public CollaboratorController(CollaboratorService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResult<List<CollaboratorSummaryResponse>>> findAll(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean excludeSelf, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Colaboradores retornados com sucesso",
                service.findAll(user, excludeSelf), request.getRequestURI()));
    }

    @GetMapping("/pending-count")
    public ResponseEntity<ApiResult<List<CollaboratorPendingCountResponse>>> pendingCount(
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Resumo dos colaboradores retornado com sucesso",
                service.findPendingCount(user), request.getRequestURI()));
    }

    @GetMapping("/{collaboratorId}")
    public ResponseEntity<ApiResult<CollaboratorDetailsResponse>> findById(
            @PathVariable Long collaboratorId, @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Colaborador retornado com sucesso",
                service.findById(collaboratorId, user), request.getRequestURI()));
    }

    @GetMapping("/{collaboratorId}/events")
    public ResponseEntity<ApiResult<CollaboratorEventsResponse>> findEventsById(
            @PathVariable Long collaboratorId, @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Eventos do colaborador retornados com sucesso",
                service.findEventsById(collaboratorId, user), request.getRequestURI()));
    }

    @GetMapping("/{collaboratorId}/profile")
    public ResponseEntity<ApiResult<CollaboratorProfileResponse>> findProfileById(
            @PathVariable Long collaboratorId, @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Perfil do colaborador retornado com sucesso",
                service.findProfileById(collaboratorId, user), request.getRequestURI()));
    }

    @PostMapping
    public ResponseEntity<ApiResult<CollaboratorDetailsResponse>> create(
            @Valid @RequestBody CreateCollaboratorRequest body, @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) {
        var response = service.create(body, user);
        return ResponseEntity.created(URI.create("/collaborators/" + response.id()))
                .body(ApiResult.success("Colaborador pré-cadastrado com sucesso", response, request.getRequestURI()));
    }

    @PutMapping("/{collaboratorId}")
    public ResponseEntity<ApiResult<CollaboratorDetailsResponse>> update(@PathVariable Long collaboratorId,
            @Valid @RequestBody UpdateCollaboratorRequest body, @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Colaborador atualizado com sucesso",
                service.update(collaboratorId, body, user), request.getRequestURI()));
    }

    @PatchMapping("/{collaboratorId}/status")
    public ResponseEntity<ApiResult<CollaboratorDetailsResponse>> changeStatus(@PathVariable Long collaboratorId,
            @Valid @RequestBody CollaboratorStatusRequest body, @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Status do colaborador atualizado com sucesso",
                service.changeStatus(collaboratorId, body, user), request.getRequestURI()));
    }
}
