package com.astro.api.workspace.controller;

import com.astro.api.common.response.ApiResult;
import com.astro.api.workspace.dto.request.CreateWorkspaceRequest;
import com.astro.api.workspace.dto.response.RegisterWorkspaceResponse;
import com.astro.api.workspace.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class WorkspaceController {
    private final WorkspaceService workspaceService;
    public WorkspaceController(WorkspaceService workspaceService) { this.workspaceService = workspaceService; }

    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            encoding = @Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE)
        )
    )
    @PostMapping(value = "/register-workspace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Conclui o cadastro inicial de um workspace", description = "Endpoint público. Envie a parte JSON `data` e a planilha XLS/XLSX obrigatória em `file`.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Workspace criado com todos os colaboradores da planilha"),
            @ApiResponse(responseCode = "400", description = "Multipart ou dados inválidos"),
            @ApiResponse(responseCode = "422", description = "Planilha possui erros; nenhum dado foi persistido"),
            @ApiResponse(responseCode = "409", description = "Conflito estrutural")
    })
    public ResponseEntity<ApiResult<RegisterWorkspaceResponse>> register(
            @Parameter(description = "Dados do workspace em JSON")
            @RequestPart("data") @Valid CreateWorkspaceRequest data,
            @Parameter(description = "Planilha XLS ou XLSX de colaboradores")
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        RegisterWorkspaceResponse response = workspaceService.register(data, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.success("Workspace cadastrado com sucesso", response, request.getRequestURI()));
    }
}
