package com.astro.api.workspace.controller;

import com.astro.api.workspace.swagger.WorkspaceSwagger;

import com.astro.api.common.response.ApiResult;
import com.astro.api.workspace.dto.request.CreateWorkspaceRequest;
import com.astro.api.workspace.dto.request.QueueManagerEmailRequest;
import com.astro.api.workspace.dto.response.RegisterWorkspaceResponse;
import com.astro.api.workspace.service.PreRegistrationQueueService;
import com.astro.api.workspace.service.WorkspaceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class WorkspaceController implements WorkspaceSwagger {
    private final WorkspaceService workspaceService;
    private final PreRegistrationQueueService preRegistrationQueueService;

    public WorkspaceController(
            WorkspaceService workspaceService,
            PreRegistrationQueueService preRegistrationQueueService) {
        this.workspaceService = workspaceService;
        this.preRegistrationQueueService = preRegistrationQueueService;
    }

    @PostMapping(value = "/register-workspace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResult<RegisterWorkspaceResponse>> register(
            @RequestPart("data") @Valid CreateWorkspaceRequest data,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        RegisterWorkspaceResponse response = workspaceService.register(data, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.success("Workspace cadastrado com sucesso", response, request.getRequestURI()));
    }

    @PostMapping("/add-manager-email")
    public ResponseEntity<Void> enqueueManagerEmail(@RequestBody @Valid QueueManagerEmailRequest request) {
        preRegistrationQueueService.enqueueManagerEmail(request.email());
        return ResponseEntity.noContent().build();
    }
}
