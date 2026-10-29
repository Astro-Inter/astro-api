package com.astro.api.conformity.controller;

import com.astro.api.conformity.swagger.UnitConformitySwagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.conformity.dto.request.*;
import com.astro.api.conformity.dto.response.UnitConformityResponse;
import com.astro.api.conformity.service.UnitConformityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/conformities/units")
@PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
public class UnitConformityController implements UnitConformitySwagger {
    private final UnitConformityService service;
    public UnitConformityController(UnitConformityService service) { this.service = service; }
    @GetMapping
    public ResponseEntity<ApiResult<List<UnitConformityResponse>>> list(@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Conformidades retornadas", service.findAll(user), request.getRequestURI()));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiResult<UnitConformityResponse>> details(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Conformidade retornada", service.findById(id, user), request.getRequestURI()));
    }
    @PostMapping
    public ResponseEntity<ApiResult<UnitConformityResponse>> create(@Valid @RequestBody CreateUnitConformityRequest body, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        UnitConformityResponse saved = service.create(body, user);
        return ResponseEntity.created(URI.create("/conformities/units/" + saved.id())).body(ApiResult.success("Conformidade criada", saved, request.getRequestURI()));
    }
    @PutMapping("/{id}")
    public ResponseEntity<ApiResult<UnitConformityResponse>> update(@PathVariable Long id, @Valid @RequestBody UpdateConformityRequest body, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Conformidade atualizada", service.update(id, body, user), request.getRequestURI()));
    }
    @PatchMapping("/{id}/applicability")
    public ResponseEntity<ApiResult<UnitConformityResponse>> applicability(@PathVariable Long id, @Valid @RequestBody ConformityApplicabilityRequest body, @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Aplicabilidade atualizada", service.changeApplicability(id, body, user), request.getRequestURI()));
    }
}
