package com.astro.api.user.swagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.user.dto.request.UpdateAccountDetailsRequest;
import com.astro.api.user.dto.response.AccountDetailsResponse;
import com.astro.api.user.dto.response.AccountSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@Tag(name = "Minha conta", description = "Consulta e edição dos dados da própria conta.")
@SecurityRequirement(name = "bearerAuth")
public interface AccountSwagger {

    @Operation(summary = "Consulta o resumo da própria conta", description = "Identifica a conta exclusivamente pelo token Firebase. Retorna somente nome, email e profilePhotoUrl, sem NRs ou vínculos administrativos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumo no envelope ApiResult; foto ausente retorna null"),
            @ApiResponse(responseCode = "403", description = "Token ausente/inválido ou perfil sem acesso, conforme a segurança existente"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado (ApiResult de erro)"),
            @ApiResponse(responseCode = "500", description = "Erro interno ou falha ao gerar a URL da foto (ApiResult de erro)"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível (ApiResult de erro)")
    })
    ResponseEntity<ApiResult<AccountSummaryResponse>> summary(
             AuthenticatedUser authenticatedUser, HttpServletRequest request);

    @Operation(summary = "Consulta os detalhes da própria conta", description = "Retorna os nove campos da conta web, com IDs e nomes de cargo/unidade. CPF, modalidade, cargo e foto ausentes permanecem null. Não recebe senha nem exige confirmação de senha no frontend como autorização.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhes no envelope ApiResult"),
            @ApiResponse(responseCode = "403", description = "Token ausente/inválido, perfil sem acesso ou vínculo fora do workspace"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado (ApiResult de erro)"),
            @ApiResponse(responseCode = "500", description = "Erro interno ou falha ao gerar foto (ApiResult de erro)"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível (ApiResult de erro)")
    })
    ResponseEntity<ApiResult<AccountDetailsResponse>> details(
             AuthenticatedUser authenticatedUser, HttpServletRequest request);

    @Operation(summary = "Substitui os seis campos editáveis da própria conta", description = "Exige name, cpf, email, roleId, unitId e workModel, sem nulos ou campos extras. GESTOR mantém cargo/unidade; GESTOR_WORKSPACE pode escolher vínculos ativos no próprio workspace. Preserva vínculos inativos atuais. Não recebe senha ou foto. E-mail alterado é sincronizado com Firebase e marcado como não verificado; confirmação deve ocorrer no fluxo Firebase do cliente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhes atualizados no envelope ApiResult, após commit e sincronização de e-mail"),
            @ApiResponse(responseCode = "400", description = "Campos omitidos, nulos, desconhecidos, formatos inválidos ou novo vínculo inativo (ApiResult de erro)"),
            @ApiResponse(responseCode = "403", description = "Token ausente/inválido, perfil sem acesso ou mudança de vínculo sem permissão"),
            @ApiResponse(responseCode = "404", description = "Usuário, cargo ou unidade não encontrados no workspace (ApiResult de erro)"),
            @ApiResponse(responseCode = "409", description = "CPF/e-mail duplicado ou identidade Firebase divergente (ApiResult de erro)"),
            @ApiResponse(responseCode = "500", description = "Falha interna/Firebase; sem sucesso parcial, com compensação ou contenção (ApiResult de erro)"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível (ApiResult de erro)")
    })
    ResponseEntity<ApiResult<AccountDetailsResponse>> update(  UpdateAccountDetailsRequest updateAccountDetailsRequest,
             AuthenticatedUser authenticatedUser, HttpServletRequest request);
}
