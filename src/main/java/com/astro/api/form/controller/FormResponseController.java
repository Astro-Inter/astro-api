package com.astro.api.form.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.form.dto.request.*;
import com.astro.api.form.dto.response.*;
import com.astro.api.form.model.FormStatus;
import com.astro.api.form.service.FormResponseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Operação concluída no envelope ApiResult"),
        @ApiResponse(responseCode = "400", description = "Identificador, parâmetros ou dados inválidos"),
        @ApiResponse(responseCode = "401", description = "Token Firebase ausente ou inválido; envelope ApiResult"),
        @ApiResponse(responseCode = "403", description = "Conta, unidade, perfil ou permissão sem acesso; envelope ApiResult"),
        @ApiResponse(responseCode = "404", description = "Recurso inexistente ou fora do escopo autorizado"),
        @ApiResponse(responseCode = "503", description = "Conexão com a persistência indisponível")
})
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
    @Operation(summary = "Lista formulários recebidos com viewed; inclui pendentes e concluídos, inclusive reprovados, sem filtro de status")
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
    @Operation(summary = "Consulta o formulário destinado ao gestor autenticado, retorna as coordenadas da unidade e registra a visualização")
    public ResponseEntity<ApiResult<FormResponseDetailsResponse>> receivedById(@PathVariable("id") String id,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Formulário recebido retornado com sucesso",
                formResponseService.findReceivedById(id, principal), request.getRequestURI()));
    }

    @ApiResponses({
            @ApiResponse(responseCode = "409", description = "Formulário já respondido, alterado ou não pendente"),
            @ApiResponse(responseCode = "415", description = "Formato de conteúdo não suportado")
    })
    @PostMapping(value = "/{id}/responses", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Envia resposta única definitiva sem imagens; localização é verificada somente no mobile")
    public ResponseEntity<ApiResult<FormResponseDetailsResponse>> submitJson(@PathVariable("id") String id,
            @Valid @RequestBody FormResponseRequest response, @AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletRequest request) {
        return submitted(id, response, List.of(), principal, request);
    }

    @ApiResponses({
            @ApiResponse(responseCode = "409", description = "Formulário já respondido, alterado ou não pendente"),
            @ApiResponse(responseCode = "413", description = "Upload excede o tamanho permitido"),
            @ApiResponse(responseCode = "415", description = "Formato de conteúdo não suportado")
    })
    @PostMapping(value = "/{id}/responses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Envia respostas e imagens JPEG/PNG; partes response (JSON) e files; sem envio de GPS")
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

    @ApiResponse(responseCode = "409", description = "Formulário ainda não respondido")
    @GetMapping("/{id}/responses")
    @Operation(summary = "Consulta respostas e análise para participantes autorizados")
    public ResponseEntity<ApiResult<FormResponseDetailsResponse>> responses(@PathVariable("id") String id,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Respostas retornadas com sucesso",
                formResponseService.findResponses(id, principal), request.getRequestURI()));
    }

    @ApiResponse(responseCode = "409", description = "Formulário não respondido, já analisado ou alterado")
    @PostMapping("/{id}/review")
    @Operation(summary = "Registra análise definitiva do remetente ou gestor do workspace: approved ou rejected", description = "Aprovar formulário com NR gera conformidade da unidade indicada ou da unidade do destinatário. Validade: data da resposta mais meses de reciclagem. Repetir approved reconcilia sem duplicar; trocar decisão retorna 409. MongoDB e PostgreSQL têm transações separadas.")
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
