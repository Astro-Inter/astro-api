package com.astro.api.conformity.controller;

import com.astro.api.conformity.swagger.CollaboratorConformitySwagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.conformity.dto.response.ConformityResponse;
import com.astro.api.conformity.service.ConformityService;
import com.astro.api.conformity.service.ConformityImportService;
import com.astro.api.conformity.dto.response.ConformityImportResponse;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.net.URI;
import jakarta.validation.Valid;
import com.astro.api.conformity.dto.request.CreateConformityRequest;
import com.astro.api.conformity.dto.request.UpdateConformityRequest;
import com.astro.api.conformity.dto.request.ConformityApplicabilityRequest;

@RestController
@RequestMapping("/conformities/collaborators")
@PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
public class CollaboratorConformityController implements CollaboratorConformitySwagger {
    private final ConformityService conformityService;
    private final ConformityImportService conformityImportService;

    public CollaboratorConformityController(ConformityService conformityService, ConformityImportService conformityImportService) {
        this.conformityService = conformityService;
        this.conformityImportService = conformityImportService;
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<ApiResult<ConformityImportResponse>> importFile(@RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Importação processada", conformityImportService.importFile(file, principal), request.getRequestURI()));
    }

    @PatchMapping("/{conformityId}/applicability")
    public ResponseEntity<ApiResult<ConformityResponse>> changeApplicability(@PathVariable Long conformityId,
            @Valid @RequestBody ConformityApplicabilityRequest body,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Aplicabilidade atualizada com sucesso",
                conformityService.changeApplicability(conformityId, body, principal), request.getRequestURI()));
    }

    @PutMapping("/{conformityId}")
    public ResponseEntity<ApiResult<ConformityResponse>> update(@PathVariable Long conformityId,
            @Valid @RequestBody UpdateConformityRequest body,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Conformidade atualizada com sucesso",
                conformityService.update(conformityId, body, principal), request.getRequestURI()));
    }

    @PostMapping
    public ResponseEntity<ApiResult<ConformityResponse>> create(@Valid @RequestBody CreateConformityRequest body,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        ConformityResponse conformityResponse = conformityService.create(body, principal);
        return ResponseEntity.created(URI.create("/conformities/collaborators/" + conformityResponse.id()))
                .body(ApiResult.success("Conformidade criada com sucesso", conformityResponse, request.getRequestURI()));
    }

    @GetMapping("/{conformityId}")
    public ResponseEntity<ApiResult<ConformityResponse>> findById(@PathVariable Long conformityId,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Conformidade retornada com sucesso",
                conformityService.findById(conformityId, principal), request.getRequestURI()));
    }

    @GetMapping
    public ResponseEntity<ApiResult<List<ConformityResponse>>> findAll(
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Conformidades retornadas com sucesso",
                conformityService.findAll(principal), request.getRequestURI()));
    }
}
