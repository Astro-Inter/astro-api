package com.astro.api.cargo.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.cargo.dto.request.CargoRequest;
import com.astro.api.cargo.dto.request.CargoStatusRequest;
import com.astro.api.cargo.dto.request.UpdateCargoNrsRequest;
import com.astro.api.cargo.dto.response.CargoNrOptionResponse;
import com.astro.api.cargo.dto.response.CargoNrResponse;
import com.astro.api.cargo.dto.response.CargoResponse;
import com.astro.api.cargo.service.CargoService;
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
@RequestMapping("/cargos")
@PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
public class CargoController {
    private final CargoService service;

    public CargoController(CargoService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cria cargo no workspace do gestor")
    public ResponseEntity<ApiResult<Void>> create(@Valid @RequestBody CargoRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        service.create(body, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.success(
                "Cargo criado com sucesso", null, request.getRequestURI()));
    }

    @PatchMapping("/{id_cargo}/status")
    @Operation(summary = "Alterna o status do cargo entre ativo e inativo")
    public ResponseEntity<ApiResult<Boolean>> toggleStatus(@PathVariable("id_cargo") Long cargoId,
            @Valid @RequestBody CargoStatusRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Status do cargo alterado com sucesso",
                service.toggleStatus(body.firebaseUid(), cargoId, user), request.getRequestURI()));
    }

    @PutMapping("/{id_cargo}")
    @Operation(summary = "Atualiza nome e status do cargo")
    public ResponseEntity<ApiResult<CargoResponse>> update(@PathVariable("id_cargo") Long cargoId,
            @Valid @RequestBody CargoRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Cargo atualizado com sucesso",
                service.update(cargoId, body, user), request.getRequestURI()));
    }

    @PostMapping("/{id_cargo}/nrs")
    @Operation(summary = "Vincula ou desvincula as NRs informadas do cargo")
    public ResponseEntity<ApiResult<Void>> updateNrs(@PathVariable("id_cargo") Long cargoId,
            @Valid @RequestBody UpdateCargoNrsRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        service.updateNrs(cargoId, body, user);
        return ResponseEntity.ok(ApiResult.success("NRs do cargo atualizadas com sucesso",
                null, request.getRequestURI()));
    }

    @GetMapping
    @Operation(summary = "Lista cargos do workspace do gestor")
    public ResponseEntity<ApiResult<List<CargoResponse>>> findAll(
            @RequestParam("firebase_uid") String uid,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Cargos retornados com sucesso",
                service.findAll(uid, user), request.getRequestURI()));
    }

    @GetMapping("/{id_cargo}/nrs")
    @Operation(summary = "Consulta NRs vinculadas ao cargo")
    public ResponseEntity<ApiResult<List<CargoNrResponse>>> findNrs(
            @PathVariable("id_cargo") Long cargoId, @RequestParam("firebase_uid") String uid,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("NRs do cargo retornadas com sucesso",
                service.findNrs(uid, cargoId, user), request.getRequestURI()));
    }

    @GetMapping("/{id_cargo}/nrs/opcoes")
    @Operation(summary = "Lista opções de NRs e indica os vínculos do cargo")
    public ResponseEntity<ApiResult<List<CargoNrOptionResponse>>> findNrOptions(
            @PathVariable("id_cargo") Long cargoId, @RequestParam("firebase_uid") String uid,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Opções de NRs retornadas com sucesso",
                service.findNrOptions(uid, cargoId, user), request.getRequestURI()));
    }
}
