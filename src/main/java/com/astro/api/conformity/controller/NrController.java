package com.astro.api.conformity.controller;

import com.astro.api.conformity.swagger.NrSwagger;

import com.astro.api.common.response.ApiResult;
import com.astro.api.conformity.dto.response.NrResponse;
import com.astro.api.conformity.repository.NrCatalogRepository;
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
public class NrController implements NrSwagger {
    private final NrCatalogRepository nrCatalogRepository;

    public NrController(NrCatalogRepository nrCatalogRepository) {
        this.nrCatalogRepository = nrCatalogRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResult<List<NrResponse>>> findAll(HttpServletRequest request) {
        List<NrResponse> nrs = nrCatalogRepository.findAllByOrderByCodeNrAsc().stream()
                .map(nrCatalog -> new NrResponse(nrCatalog.getCodeNr(), nrCatalog.getTitle()))
                .toList();
        return ResponseEntity.ok(ApiResult.success(
                "NRs retornadas com sucesso", nrs, request.getRequestURI()));
    }
}
