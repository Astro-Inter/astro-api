package com.astro.api.workspace.swagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.workspace.dto.request.UpdateWorkspaceRequest;
import com.astro.api.workspace.dto.request.WorkspaceImportRequest;
import com.astro.api.workspace.dto.request.WorkspaceManagerTransferRequest;
import com.astro.api.workspace.dto.response.WorkspaceImportResponse;
import com.astro.api.workspace.dto.response.WorkspaceManagerTransferResponse;
import com.astro.api.workspace.dto.response.WorkspaceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Configurações do workspace", description = "Dados do workspace, importação de colaboradores e transferência de gestão.")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Token Firebase ausente ou inválido"),
    @ApiResponse(responseCode = "403", description = "Exige gestor de workspace ativo"),
    @ApiResponse(responseCode = "409", description = "Conflito de identidade ou unicidade")
})
public interface WorkspaceSettingsSwagger {

    @Operation(summary = "Consulta nome e CNPJ do workspace autenticado")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<WorkspaceResponse>> find( AuthenticatedUser user,
                                                            HttpServletRequest request);

    @Operation(summary = "Transfere a responsabilidade do workspace para outro gestor ativo",
            description = "O responsável atual passa a GESTOR e o escolhido passa a GESTOR_WORKSPACE na mesma transação.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Responsabilidade transferida na mesma transação"),
        @ApiResponse(responseCode = "400", description = "Identificador inválido, própria conta ou gestor inativo"),
        @ApiResponse(responseCode = "404", description = "Gestor não encontrado no próprio workspace")
    })
    ResponseEntity<ApiResult<WorkspaceManagerTransferResponse>> transferManager(
              WorkspaceManagerTransferRequest body,
             AuthenticatedUser user, HttpServletRequest request);

    @Operation(summary = "Atualiza nome e CNPJ do workspace autenticado")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<WorkspaceResponse>> update(  UpdateWorkspaceRequest body,
             AuthenticatedUser user, HttpServletRequest request);

    @Operation(summary = "Sincroniza colaboradores, cargos e novas unidades por Excel",
            description = "Identifica colaboradores por CPF. Cria novos, atualiza alterações e ignora os demais. "
                    + "Novas sedes exigem endereço na parte JSON data.units. Limite: 10 MB.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            encoding = @Encoding(name = "data", contentType = MediaType.APPLICATION_JSON_VALUE)))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resumo de criados, atualizados e ignorados"),
        @ApiResponse(responseCode = "422", description = "Erros por linha/campo; nenhum dado gravado"),
        @ApiResponse(responseCode = "413", description = "Arquivo acima de 10 MB")
    })
    ResponseEntity<ApiResult<WorkspaceImportResponse>> importFile(
             MultipartFile file,
              WorkspaceImportRequest body,
             AuthenticatedUser user, HttpServletRequest request);
}
