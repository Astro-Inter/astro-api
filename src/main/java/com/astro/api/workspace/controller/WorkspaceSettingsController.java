package com.astro.api.workspace.controller;

import com.astro.api.workspace.swagger.WorkspaceSettingsSwagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.workspace.dto.request.UpdateWorkspaceRequest;
import com.astro.api.workspace.dto.request.WorkspaceImportRequest;
import com.astro.api.workspace.dto.request.WorkspaceManagerTransferRequest;
import com.astro.api.workspace.dto.response.WorkspaceManagerTransferResponse;
import com.astro.api.workspace.dto.response.WorkspaceImportResponse;
import com.astro.api.workspace.dto.response.WorkspaceResponse;
import com.astro.api.workspace.service.WorkspaceImportService;
import com.astro.api.workspace.service.WorkspaceSettingsService;
import com.astro.api.workspace.service.WorkspaceManagerTransferService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/workspace")
@PreAuthorize("hasRole('GESTOR_WORKSPACE')")
public class WorkspaceSettingsController implements WorkspaceSettingsSwagger {
    private final WorkspaceSettingsService workspaceSettingsService;
    private final WorkspaceImportService workspaceImportService;
    private final WorkspaceManagerTransferService workspaceManagerTransferService;

    public WorkspaceSettingsController(WorkspaceSettingsService workspaceSettingsService, WorkspaceImportService workspaceImportService,
            WorkspaceManagerTransferService workspaceManagerTransferService) {
        this.workspaceSettingsService = workspaceSettingsService;
        this.workspaceImportService = workspaceImportService;
        this.workspaceManagerTransferService = workspaceManagerTransferService;
    }

    @GetMapping
    public ResponseEntity<ApiResult<WorkspaceResponse>> find(@AuthenticationPrincipal AuthenticatedUser user,
                                                            HttpServletRequest request) {
        WorkspaceResponse workspaceResponse = workspaceSettingsService.find(user);
        ApiResult<WorkspaceResponse> result = ApiResult.success(
                "Workspace retornado com sucesso", workspaceResponse, request.getRequestURI());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/manager-transfer")
    public ResponseEntity<ApiResult<WorkspaceManagerTransferResponse>> transferManager(
            @Valid @RequestBody WorkspaceManagerTransferRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        WorkspaceManagerTransferResponse response = workspaceManagerTransferService.transfer(body, user);
        ApiResult<WorkspaceManagerTransferResponse> result = ApiResult.success(
                "Responsabilidade do workspace transferida com sucesso", response, request.getRequestURI());
        return ResponseEntity.ok(result);
    }

    @PutMapping
    public ResponseEntity<ApiResult<WorkspaceResponse>> update(@Valid @RequestBody UpdateWorkspaceRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        WorkspaceResponse workspaceResponse = workspaceSettingsService.update(body, user);
        ApiResult<WorkspaceResponse> result = ApiResult.success(
                "Workspace atualizado com sucesso", workspaceResponse, request.getRequestURI());
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResult<WorkspaceImportResponse>> importFile(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "data", required = false) @Valid WorkspaceImportRequest body,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        WorkspaceImportRequest importRequest = body == null ? new WorkspaceImportRequest(List.of()) : body;
        WorkspaceImportResponse importResponse = workspaceImportService.importFile(file, importRequest, user);
        ApiResult<WorkspaceImportResponse> result = ApiResult.success(
                "Planilha sincronizada com sucesso", importResponse, request.getRequestURI());
        return ResponseEntity.ok(result);
    }
}
