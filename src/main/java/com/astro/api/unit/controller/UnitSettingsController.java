package com.astro.api.unit.controller;

import com.astro.api.unit.swagger.UnitSettingsSwagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.dto.response.UnitResponse;
import com.astro.api.unit.service.UnitSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/units")
@PreAuthorize("hasRole('GESTOR_WORKSPACE')")
public class UnitSettingsController implements UnitSettingsSwagger {
    private final UnitSettingsService unitSettingsService;

    public UnitSettingsController(UnitSettingsService unitSettingsService) {
        this.unitSettingsService = unitSettingsService;
    }

    @PostMapping
    public ResponseEntity<ApiResult<UnitResponse>> create(@Valid @RequestBody UnitRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        UnitResponse response = unitSettingsService.create(body, user);
        URI location = URI.create("/units/" + response.unitId());
        ApiResult<UnitResponse> result = ApiResult.success(
                "Unidade cadastrada com sucesso", response, request.getRequestURI());
        return ResponseEntity.created(location).body(result);
    }

    @PutMapping("/{unitId}")
    public ResponseEntity<ApiResult<UnitResponse>> update(@PathVariable Long unitId,
            @Valid @RequestBody UnitRequest body, @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) {
        UnitResponse response = unitSettingsService.update(unitId, body, user);
        ApiResult<UnitResponse> result = ApiResult.success(
                "Unidade atualizada com sucesso", response, request.getRequestURI());
        return ResponseEntity.ok(result);
    }
}
