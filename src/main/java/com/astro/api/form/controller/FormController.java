package com.astro.api.form.controller;

import com.astro.api.form.swagger.FormSwagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.form.dto.request.FormRequest;
import com.astro.api.form.dto.response.FormDetailsResponse;
import com.astro.api.form.dto.response.FormPageResponse;
import com.astro.api.form.dto.response.FormRecipientsResponse;
import com.astro.api.form.model.FormStatus;
import com.astro.api.form.service.FormService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.beans.PropertyEditorSupport;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/forms")
@PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
public class FormController implements FormSwagger {
    private final FormService formService;

    public FormController(FormService formService) {
        this.formService = formService;
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

    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResult<FormDetailsResponse>> publish(@PathVariable("id") String id,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Formulário publicado com sucesso",
                formService.publish(id, principal), request.getRequestURI()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        formService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResult<FormDetailsResponse>> update(@PathVariable("id") String id,
            @Valid @RequestBody FormRequest body, @AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Formulário atualizado com sucesso",
                formService.update(id, body, principal), request.getRequestURI()));
    }

    @PostMapping
    public ResponseEntity<ApiResult<FormDetailsResponse>> create(@Valid @RequestBody FormRequest body,
            @AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        FormDetailsResponse form = formService.create(body, principal);
        return ResponseEntity.created(URI.create("/forms/" + form.id())).body(ApiResult.success(
                "Formulário criado com sucesso", form, request.getRequestURI()));
    }

    @GetMapping("/recipients")
    public ResponseEntity<ApiResult<FormRecipientsResponse>> findRecipients(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Destinatários retornados com sucesso",
                formService.findRecipients(authenticatedUser), request.getRequestURI()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResult<FormDetailsResponse>> findById(
            @PathVariable("id") String id, @AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Formulário retornado com sucesso",
                formService.findById(id, principal), request.getRequestURI()));
    }

    @GetMapping
    public ResponseEntity<ApiResult<FormPageResponse>> findAll(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) FormStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Formulários retornados com sucesso",
                formService.findAll(principal, search, status, page, size), request.getRequestURI()));
    }
}
