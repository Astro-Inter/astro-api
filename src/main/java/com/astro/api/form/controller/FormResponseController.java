package com.astro.api.form.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.form.dto.request.*;
import com.astro.api.form.dto.response.*;
import com.astro.api.form.model.FormStatus;
import com.astro.api.form.service.FormResponseService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.beans.PropertyEditorSupport;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/forms")
@PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
public class FormResponseController {
    private final FormResponseService formResponseService;
    public FormResponseController(FormResponseService formResponseService) {
        this.formResponseService = formResponseService;
    }

    @InitBinder
    public void configureStatusBinding(WebDataBinder binder) {
        binder.registerCustomEditor(FormStatus.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(FormStatus.fromValue(text));
            }
        });
    }

    @GetMapping("/received")
    @Operation(summary = "Lista somente os formulários recebidos pelo gestor autenticado no mobile")
    public ResponseEntity<ApiResult<FormReceivedPageResponse>> received(@AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "nrCode", required = false) Integer nrCode,
            @RequestParam(name = "status", required = false) FormStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Formulários recebidos retornados com sucesso",
                formResponseService.findReceived(principal, search, nrCode, status, page, size), request.getRequestURI()));
    }

    @GetMapping("/received/{id}")
    @Operation(summary = "Consulta o formulário destinado ao gestor autenticado e as coordenadas da unidade para o mobile")
    public ResponseEntity<ApiResult<FormResponseDetailsResponse>> receivedById(@PathVariable("id") String id,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Formulário recebido retornado com sucesso",
                formResponseService.findReceivedById(id, principal), request.getRequestURI()));
    }

    @PostMapping(value = "/{id}/responses", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Envia resposta única com registro de GPS, sem validação de distância; sem imagens")
    public ResponseEntity<ApiResult<FormResponseDetailsResponse>> submitJson(@PathVariable("id") String id,
            @Valid @RequestBody FormResponseRequest response, @AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletRequest request) {
        return submitted(id, response, List.of(), principal, request);
    }

    @PostMapping(value = "/{id}/responses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Envia respostas e imagens JPEG/PNG com registro de GPS, sem validação de distância; partes response (JSON) e files")
    public ResponseEntity<ApiResult<FormResponseDetailsResponse>> submitMultipart(@PathVariable("id") String id,
            @Valid @RequestPart("response") FormResponseRequest response,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return submitted(id, response, files, principal, request);
    }

    private ResponseEntity<ApiResult<FormResponseDetailsResponse>> submitted(String id, FormResponseRequest response,
            List<MultipartFile> files, AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Respostas enviadas com sucesso",
                formResponseService.submit(id, response, files, principal), request.getRequestURI()));
    }

    @GetMapping("/{id}/responses")
    @Operation(summary = "Consulta respostas, registros de GPS e análise para participantes autorizados")
    public ResponseEntity<ApiResult<FormResponseDetailsResponse>> responses(@PathVariable("id") String id,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Respostas retornadas com sucesso",
                formResponseService.findResponses(id, principal), request.getRequestURI()));
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Registra análise definitiva do remetente ou gestor do workspace: approved ou rejected")
    public ResponseEntity<ApiResult<FormResponseDetailsResponse>> review(@PathVariable("id") String id,
            @Valid @RequestBody FormReviewRequest review, @AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Análise registrada com sucesso",
                formResponseService.review(id, review, principal), request.getRequestURI()));
    }

    @GetMapping("/{id}/images/{imageId}")
    @Operation(summary = "Autoriza a visualização de uma imagem deste formulário por URL privada temporária")
    public ResponseEntity<ApiResult<FormImageDownloadResponse>> image(@PathVariable("id") String id,
            @PathVariable("imageId") String imageId, @AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResult.success("Imagem autorizada com sucesso",
                formResponseService.image(id, imageId, principal), request.getRequestURI()));
    }
}
