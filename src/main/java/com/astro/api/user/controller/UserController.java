package com.astro.api.user.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.user.dto.response.UserProfileResponse;
import com.astro.api.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/user/me")
    @Operation(summary = "Retorna o perfil do usuário autenticado", description = "Retorna os dados do próprio usuário, a URL temporária da foto de perfil no R2 e as NRs ativas vinculadas ao seu cargo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil retornado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    })
    public ResponseEntity<ApiResult<UserProfileResponse>> me(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        UserProfileResponse profile = userService.findProfileByFirebaseUid(authenticatedUser.firebaseUid());
        return ResponseEntity.ok(ApiResult.success("Perfil retornado com sucesso", profile, request.getRequestURI()));
    }

    @PutMapping(value = "/user/me/profile-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Atualiza a foto de perfil do usuário autenticado", description = "Recebe o arquivo no campo file, envia ao R2 e vincula o caminho ao usuário. Aceita JPEG, PNG ou WebP de até 5 MB.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Foto de perfil atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Arquivo ausente, inválido ou acima do limite de 5 MB"),
            @ApiResponse(responseCode = "413", description = "Upload excede o limite da requisição"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    })
    public ResponseEntity<Void> updateProfilePhoto(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestPart("file") MultipartFile file) {
        userService.updateProfilePhoto(authenticatedUser.firebaseUid(), file);
        return ResponseEntity.noContent().build();
    }
}
