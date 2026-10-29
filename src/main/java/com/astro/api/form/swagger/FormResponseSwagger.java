package com.astro.api.form.swagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.form.dto.request.*;
import com.astro.api.form.dto.response.*;
import com.astro.api.form.model.FormStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Respostas de formulários", description = "Respostas mobile, imagens e análise das respostas.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Operação concluída no envelope ApiResult"),
        @ApiResponse(responseCode = "400", description = "Identificador, parâmetros ou dados inválidos"),
        @ApiResponse(responseCode = "401", description = "Token Firebase ausente ou inválido; envelope ApiResult"),
        @ApiResponse(responseCode = "403", description = "Conta, unidade, perfil ou permissão sem acesso; envelope ApiResult"),
        @ApiResponse(responseCode = "404", description = "Recurso inexistente ou fora do escopo autorizado"),
        @ApiResponse(responseCode = "503", description = "Conexão com a persistência indisponível")
})
public interface FormResponseSwagger {

    @Operation(summary = "Lista formulários recebidos com viewed; inclui pendentes e concluídos, inclusive reprovados, sem filtro de status")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<FormReceivedPageResponse>> received( AuthenticatedUser principal,
             String search,
             Integer nrCode,
             FormStatus status,
             int page,
             int size, HttpServletRequest request);

    @Operation(summary = "Consulta o formulário destinado ao gestor autenticado, retorna as coordenadas da unidade e registra a visualização")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<FormResponseDetailsResponse>> receivedById( String id,
             AuthenticatedUser principal, HttpServletRequest request);

    @ApiResponses({
            @ApiResponse(responseCode = "409", description = "Formulário já respondido, alterado ou não pendente"),
            @ApiResponse(responseCode = "415", description = "Formato de conteúdo não suportado")
    })
    @Operation(summary = "Envia resposta única definitiva sem imagens; localização é verificada somente no mobile")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<FormResponseDetailsResponse>> submitJson( String id,
              FormResponseRequest response,  AuthenticatedUser principal,
            HttpServletRequest request);

    @ApiResponses({
            @ApiResponse(responseCode = "409", description = "Formulário já respondido, alterado ou não pendente"),
            @ApiResponse(responseCode = "413", description = "Upload excede o tamanho permitido"),
            @ApiResponse(responseCode = "415", description = "Formato de conteúdo não suportado")
    })
    @Operation(summary = "Envia respostas e imagens JPEG/PNG; partes response (JSON) e files; sem envio de GPS")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<FormResponseDetailsResponse>> submitMultipart( String id,
              FormResponseRequest response,
             List<MultipartFile> files,
             AuthenticatedUser principal, HttpServletRequest request);

    @ApiResponse(responseCode = "409", description = "Formulário ainda não respondido")
    @Operation(summary = "Consulta respostas e análise para participantes autorizados")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<FormResponseDetailsResponse>> responses( String id,
             AuthenticatedUser principal, HttpServletRequest request);

    @ApiResponse(responseCode = "409", description = "Formulário não respondido, já analisado ou alterado")
    @Operation(summary = "Registra análise definitiva do remetente ou gestor do workspace: approved ou rejected", description = "Aprovar formulário com NR gera conformidade da unidade indicada ou da unidade do destinatário. Validade: data da resposta mais meses de reciclagem. Repetir approved reconcilia sem duplicar; trocar decisão retorna 409. MongoDB e PostgreSQL têm transações separadas.")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<FormResponseDetailsResponse>> review( String id,
              FormReviewRequest review,  AuthenticatedUser principal,
            HttpServletRequest request);

    @Operation(summary = "Autoriza a visualização de uma imagem deste formulário por URL privada temporária")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<FormImageDownloadResponse>> image( String id,
             String imageId,  AuthenticatedUser principal,
            HttpServletRequest request);
}
