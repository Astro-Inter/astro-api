package com.astro.api.form.swagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.form.dto.request.FormRequest;
import com.astro.api.form.dto.response.FormDetailsResponse;
import com.astro.api.form.dto.response.FormPageResponse;
import com.astro.api.form.dto.response.FormRecipientsResponse;
import com.astro.api.form.model.FormStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@Tag(name = "Formulários", description = "Criação, publicação e consulta de formulários.")
public interface FormSwagger {

    @Operation(summary = "Publica um rascunho válido, tornando-o pendente para o gestor destinatário")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<FormDetailsResponse>> publish( String id,
             AuthenticatedUser principal, HttpServletRequest request);

    @Operation(summary = "Exclui um formulário sem respostas, respeitando remetente e workspace")
    @ApiResponse(responseCode = "204", description = "Sem conteúdo")
    ResponseEntity<Void> delete( String id,
             AuthenticatedUser principal);

    @Operation(summary = "Substitui dados e perguntas de um formulário próprio ou gerenciado pelo workspace")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<FormDetailsResponse>> update( String id,
              FormRequest body,  AuthenticatedUser principal,
            HttpServletRequest request);

    @Operation(summary = "Cria um formulário pendente ou um rascunho com respostas vazias")
    @ApiResponse(responseCode = "201", description = "Recurso criado")
    ResponseEntity<ApiResult<FormDetailsResponse>> create(  FormRequest body,
             AuthenticatedUser principal, HttpServletRequest request);

    @Operation(summary = "Lista gestores ativos do workspace para selecionar o destinatário do formulário")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<FormRecipientsResponse>> findRecipients(
             AuthenticatedUser authenticatedUser, HttpServletRequest request);

    @Operation(summary = "Consulta informações e perguntas de um formulário acessível")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<FormDetailsResponse>> findById(
             String id,  AuthenticatedUser principal,
            HttpServletRequest request);

    @Operation(summary = "Lista formulários acessíveis com busca, status e paginação",
            description = "Cada item inclui reviewStatus: awaiting_response, awaiting_review, approved ou rejected. O status principal permanece inalterado.")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<FormPageResponse>> findAll(
             AuthenticatedUser principal,
             String search,
             FormStatus status,
             int page,
             int size,
            HttpServletRequest request);
}
