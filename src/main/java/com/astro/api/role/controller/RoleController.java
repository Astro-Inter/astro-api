package com.astro.api.role.controller;

import com.astro.api.role.swagger.RoleSwagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.role.dto.request.RoleRequest;
import com.astro.api.role.dto.request.RoleStatusRequest;
import com.astro.api.role.dto.request.UpdateRoleNrsRequest;
import com.astro.api.role.dto.response.RoleNrOptionResponse;
import com.astro.api.role.dto.response.RoleNrResponse;
import com.astro.api.role.dto.response.RoleResponse;
import com.astro.api.role.service.RoleService;
import com.astro.api.common.response.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/roles")
@PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
public class RoleController implements RoleSwagger {
    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResult<Void>> create(@Valid @RequestBody RoleRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        service.create(body, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.success(
                "Cargo criado com sucesso", null, request.getRequestURI()));
    }

    @PatchMapping("/{roleId}/status")
    public ResponseEntity<ApiResult<Boolean>> toggleStatus(@PathVariable("roleId") Long roleId,
            @Valid @RequestBody RoleStatusRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Status do cargo alterado com sucesso",
                service.toggleStatus(body.firebaseUid(), roleId, user), request.getRequestURI()));
    }

    @PutMapping("/{roleId}")
    public ResponseEntity<ApiResult<RoleResponse>> update(@PathVariable("roleId") Long roleId,
            @Valid @RequestBody RoleRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Cargo atualizado com sucesso",
                service.update(roleId, body, user), request.getRequestURI()));
    }

    @PostMapping("/{roleId}/nrs")
    public ResponseEntity<ApiResult<Void>> updateNrs(@PathVariable("roleId") Long roleId,
            @Valid @RequestBody UpdateRoleNrsRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        service.updateNrs(roleId, body, user);
        return ResponseEntity.ok(ApiResult.success("NRs do cargo atualizadas com sucesso",
                null, request.getRequestURI()));
    }

    @GetMapping
    public ResponseEntity<ApiResult<List<RoleResponse>>> findAll(
            @RequestParam("firebase_uid") String uid,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Cargos retornados com sucesso",
                service.findAll(uid, user), request.getRequestURI()));
    }

    @GetMapping("/{roleId}/nrs")
    public ResponseEntity<ApiResult<List<RoleNrResponse>>> findNrs(
            @PathVariable("roleId") Long roleId, @RequestParam("firebase_uid") String uid,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("NRs do cargo retornadas com sucesso",
                service.findNrs(uid, roleId, user), request.getRequestURI()));
    }

    @GetMapping("/{roleId}/nrs/options")
    public ResponseEntity<ApiResult<List<RoleNrOptionResponse>>> findNrOptions(
            @PathVariable("roleId") Long roleId, @RequestParam("firebase_uid") String uid,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Opções de NRs retornadas com sucesso",
                service.findNrOptions(uid, roleId, user), request.getRequestURI()));
    }
}
