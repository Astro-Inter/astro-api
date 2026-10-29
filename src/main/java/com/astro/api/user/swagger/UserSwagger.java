package com.astro.api.user.swagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.user.dto.response.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Perfil do usuário", description = "Perfil e foto do usuário autenticado.")
public interface UserSwagger {

    @Operation(summary = "Retorna o perfil do usuário autenticado", description = "Retorna os dados do próprio usuário, a URL temporária da foto de perfil no R2 e as NRs ativas vinculadas ao seu cargo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil retornado com sucesso"),
            @ApiResponse(responseCode = "403", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    })
    ResponseEntity<ApiResult<UserProfileResponse>> me(
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);

    @Operation(summary = "Atualiza a foto de perfil do usuário autenticado", description = "Recebe o arquivo no campo file, envia ao R2 e vincula o caminho ao usuário. Aceita JPEG, PNG ou WebP de até 5 MB.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Foto de perfil atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Arquivo ausente, inválido ou acima do limite de 5 MB"),
            @ApiResponse(responseCode = "413", description = "Upload excede o limite da requisição"),
            @ApiResponse(responseCode = "403", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    })
    ResponseEntity<Void> updateProfilePhoto(
             AuthenticatedUser authenticatedUser,
             MultipartFile file);
}
