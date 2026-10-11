package com.astro.api.cargo.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.cargo.dto.response.CargoNrOptionResponse;
import com.astro.api.cargo.dto.response.CargoNrResponse;
import com.astro.api.cargo.dto.response.CargoResponse;
import com.astro.api.cargo.service.CargoService;
import com.astro.api.common.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
