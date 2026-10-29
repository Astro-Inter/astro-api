package com.astro.api.user.swagger;

import com.astro.api.common.response.ApiResult;
import com.astro.api.user.dto.request.AccessKeyVerificationRequestDto;
import com.astro.api.user.dto.request.EmailVerificationRequestDto;
import com.astro.api.user.dto.request.UserActivationRequestDto;
import com.astro.api.user.dto.response.IdentificatedUserResponseDto;
import com.astro.api.user.model.UserType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@Tag(name = "Autenticação e ativação", description = "Identificação por e-mail, validação de chave e ativação de conta.")
@io.swagger.v3.oas.annotations.security.SecurityRequirements
public interface AuthSwagger {

    @Operation(summary = "Verifica o e-mail de um usuário", description = "Retorna o tipo e o status pelo e-mail. Para administradores, retorna ADMIN e status nulo.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuário identificado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    })
    ResponseEntity<ApiResult<IdentificatedUserResponseDto>> findUserTypeAndStatusByEmail(  EmailVerificationRequestDto dto, HttpServletRequest request);

    @Operation(summary = "Ativa um colaborador", description = "Atualiza o UID do Firebase do colaborador identificado pelo e-mail. A trigger do banco atualiza o status para ATIVO.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Colaborador ativado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Colaborador não encontrado")
    })
    ResponseEntity<Void> activateCollaborator(  UserActivationRequestDto dto);

    @Operation(summary = "Verifica uma chave de acesso", description = "Valida a chave de seis dígitos enviada por e-mail. O parâmetro opcional tipoConta assume COLABORADOR quando omitido ou vazio e exige um colaborador PRE_CADASTRADO. GESTOR_WORKSPACE valida o código de workspace no Redis sem exigir usuário cadastrado. O parâmetro não atribui perfil ao usuário.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Chave validada com sucesso"),
            @ApiResponse(responseCode = "403", description = "Chave incorreta ou ausente, ou colaborador não elegível"),
            @ApiResponse(responseCode = "400", description = "Corpo da requisição ou parâmetro tipoConta inválido; use COLABORADOR ou GESTOR_WORKSPACE")
    })
    ResponseEntity<Void> verifyAccessKey(
              AccessKeyVerificationRequestDto dto,
             UserType tipoConta);
}
