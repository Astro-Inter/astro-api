package com.astro.api.conformidade.controller;

import com.astro.api.common.response.ApiResult;
import com.astro.api.conformidade.dto.response.NrResponse;
import com.astro.api.conformidade.repository.NrCatalogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/nrs")
@PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
public class NrController {
    private final NrCatalogRepository nrCatalogRepository;

    public NrController(NrCatalogRepository nrCatalogRepository) {
        this.nrCatalogRepository = nrCatalogRepository;
    }

    @GetMapping
    @Operation(summary = "Lista os códigos de NR disponíveis para seleção")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "NRs retornadas com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado")
    })
    public ResponseEntity<ApiResult<List<NrResponse>>> findAll(HttpServletRequest request) {
        List<NrResponse> nrs = nrCatalogRepository.findAllByOrderByCodeNrAsc().stream()
                .map(nrCatalog -> new NrResponse(nrCatalog.getCodeNr(), nrCatalog.getTitle()))
                .toList();
        return ResponseEntity.ok(ApiResult.success(
                "NRs retornadas com sucesso", nrs, request.getRequestURI()));
    }
}
