package com.astro.api.workspace.swagger;

import com.astro.api.common.response.ApiResult;
import com.astro.api.workspace.dto.request.CreateWorkspaceRequest;
import com.astro.api.workspace.dto.request.QueueManagerEmailRequest;
import com.astro.api.workspace.dto.response.RegisterWorkspaceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Cadastro de workspace", description = "Pré-cadastro de gestor e criação inicial de workspace.")
@io.swagger.v3.oas.annotations.security.SecurityRequirements
public interface WorkspaceSwagger {

    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            encoding = @Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE)
        )
    )
    @Operation(summary = "Conclui o cadastro inicial de um workspace", description = "Endpoint público. Envie a parte JSON `data` e a planilha XLS/XLSX obrigatória em `file`. CPF e modalidade da linha do gestor são opcionais; quando preenchidos, são validados e importados. Os endereços das unidades são geocodificados via Geoapify no backend; falhas na busca não impedem o cadastro.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Workspace criado com todos os colaboradores da planilha"),
            @ApiResponse(responseCode = "400", description = "Multipart ou dados inválidos"),
            @ApiResponse(responseCode = "422", description = "Planilha possui erros, inclusive CPF ou modalidade do gestor inválidos; nenhum dado foi persistido"),
            @ApiResponse(responseCode = "409", description = "Conflito estrutural")
    })
    ResponseEntity<ApiResult<RegisterWorkspaceResponse>> register(
            @Parameter(description = "Dados do workspace em JSON")
              CreateWorkspaceRequest data,
            @Parameter(description = "Planilha XLS ou XLSX de colaboradores")
             MultipartFile file,
            HttpServletRequest request);

    @Operation(summary = "Enfileira e-mail de gestor", description = "Adiciona o e-mail à fila de processamento de e-mails do fluxo de criação de workspace.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "E-mail enfileirado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Corpo da requisição inválido")
    })
    ResponseEntity<Void> enqueueManagerEmail(  QueueManagerEmailRequest request);
}
