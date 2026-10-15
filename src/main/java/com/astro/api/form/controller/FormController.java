package com.astro.api.form.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.form.dto.response.FormPageResponse;
import com.astro.api.form.dto.response.FormDetailsResponse;
import com.astro.api.form.model.FormStatus;
import com.astro.api.form.service.FormService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.astro.api.form.dto.request.FormWriteRequest;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/forms")
@PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
public class FormController {
    private final FormService service;

    public FormController(FormService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "Cria um formulário pendente ou um rascunho com respostas vazias")
    public ResponseEntity<ApiResult<FormDetailsResponse>> create(@Valid @RequestBody FormWriteRequest body,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        var form = service.create(body, principal);
        return ResponseEntity.created(URI.create("/forms/" + form.id())).body(ApiResult.success(
                "Formulário criado com sucesso", form, request.getRequestURI()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta informações e perguntas de um formulário acessível")
    public ResponseEntity<ApiResult<FormDetailsResponse>> findById(
            @PathVariable("id") Long id, @AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Formulário retornado com sucesso",
                service.findById(id, principal), request.getRequestURI()));
    }

    @GetMapping
    @Operation(summary = "Lista formulários acessíveis com busca, status e paginação")
    public ResponseEntity<ApiResult<FormPageResponse>> findAll(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) FormStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Formulários retornados com sucesso",
                service.findAll(principal, search, status, page, size), request.getRequestURI()));
    }
}
