package com.astro.api.unit.controller;

import com.astro.api.unit.swagger.UnitSwagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.unit.dto.request.UpdateUnitNrsRequest;
import com.astro.api.unit.dto.response.UnitResponse;
import com.astro.api.unit.dto.response.UnitNrResponse;
import com.astro.api.unit.dto.response.UnitNrOptionResponse;
import com.astro.api.unit.service.UnitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/units")
@PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
public class UnitController implements UnitSwagger {
    private final UnitService service;

    public UnitController(UnitService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResult<List<UnitResponse>>> findAll(
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Unidades retornadas com sucesso",
                service.findAll(user), request.getRequestURI()));
    }

    @PatchMapping("/{unitId}/status")
    public ResponseEntity<ApiResult<Boolean>> toggleStatus(@PathVariable("unitId") Long unitId,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Status da unidade alterado com sucesso",
                service.toggleStatus(unitId, user), request.getRequestURI()));
    }

    @GetMapping("/{unitId}/nrs/associated")
    public ResponseEntity<ApiResult<List<UnitNrResponse>>> findNrs(@PathVariable("unitId") Long unitId,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("NRs da unidade retornadas com sucesso",
                service.findNrs(unitId, user), request.getRequestURI()));
    }

    @GetMapping("/{unitId}/nrs")
    public ResponseEntity<ApiResult<List<UnitNrOptionResponse>>> findNrOptions(@PathVariable("unitId") Long unitId,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Opções de NRs retornadas com sucesso",
                service.findNrOptions(unitId, user), request.getRequestURI()));
    }

    @PostMapping("/{unitId}/nrs")
    public ResponseEntity<ApiResult<Void>> updateNrs(@PathVariable("unitId") Long unitId,
            @Valid @RequestBody UpdateUnitNrsRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        service.updateNrs(unitId, body, user);
        return ResponseEntity.ok(ApiResult.success("NRs da unidade atualizadas com sucesso",
                null, request.getRequestURI()));
    }
}
