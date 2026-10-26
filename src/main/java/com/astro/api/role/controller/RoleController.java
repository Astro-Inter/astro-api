package com.astro.api.role.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.role.dto.request.RoleRequest;
import com.astro.api.role.dto.request.RoleStatusRequest;
import com.astro.api.role.dto.request.UpdateRoleNrsRequest;
import com.astro.api.role.dto.response.RoleNrOptionResponse;
import com.astro.api.role.dto.response.RoleNrResponse;
import com.astro.api.role.dto.response.RoleResponse;
import com.astro.api.role.service.RoleService;
import com.astro.api.common.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
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
public class RoleController {
    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cria cargo no workspace do gestor")
    public ResponseEntity<ApiResult<Void>> create(@Valid @RequestBody RoleRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        service.create(body, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.success(
                "Cargo criado com sucesso", null, request.getRequestURI()));
    }

    @PatchMapping("/{roleId}/status")
    @Operation(summary = "Alterna o status do cargo entre ativo e inativo")
    public ResponseEntity<ApiResult<Boolean>> toggleStatus(@PathVariable("roleId") Long roleId,
            @Valid @RequestBody RoleStatusRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Status do cargo alterado com sucesso",
                service.toggleStatus(body.firebaseUid(), roleId, user), request.getRequestURI()));
    }

    @PutMapping("/{roleId}")
    @Operation(summary = "Atualiza nome e status do cargo")
    public ResponseEntity<ApiResult<RoleResponse>> update(@PathVariable("roleId") Long roleId,
            @Valid @RequestBody RoleRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Cargo atualizado com sucesso",
                service.update(roleId, body, user), request.getRequestURI()));
    }

    @PostMapping("/{roleId}/nrs")
    @Operation(summary = "Vincula ou desvincula as NRs informadas do cargo",
            description = "Rejeita NRs marcadas ausentes do catálogo relacional e ignora itens desmarcados ausentes. NRs omitidas mantêm seus vínculos.")
    public ResponseEntity<ApiResult<Void>> updateNrs(@PathVariable("roleId") Long roleId,
            @Valid @RequestBody UpdateRoleNrsRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        service.updateNrs(roleId, body, user);
        return ResponseEntity.ok(ApiResult.success("NRs do cargo atualizadas com sucesso",
                null, request.getRequestURI()));
    }

    @GetMapping
    @Operation(summary = "Lista cargos do workspace do gestor")
    public ResponseEntity<ApiResult<List<RoleResponse>>> findAll(
            @RequestParam("firebase_uid") String uid,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Cargos retornados com sucesso",
                service.findAll(uid, user), request.getRequestURI()));
    }

    @GetMapping("/{roleId}/nrs")
    @Operation(summary = "Consulta NRs vinculadas ao cargo")
    public ResponseEntity<ApiResult<List<RoleNrResponse>>> findNrs(
            @PathVariable("roleId") Long roleId, @RequestParam("firebase_uid") String uid,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("NRs do cargo retornadas com sucesso",
                service.findNrs(uid, roleId, user), request.getRequestURI()));
    }

    @GetMapping("/{roleId}/nrs/options")
    @Operation(summary = "Lista opções de NRs e indica os vínculos do cargo",
            description = "Oferece NRs de colaboradores presentes no catálogo relacional e preserva as NRs já vinculadas para permitir sua remoção.")
    public ResponseEntity<ApiResult<List<RoleNrOptionResponse>>> findNrOptions(
            @PathVariable("roleId") Long roleId, @RequestParam("firebase_uid") String uid,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Opções de NRs retornadas com sucesso",
                service.findNrOptions(uid, roleId, user), request.getRequestURI()));
    }
}
