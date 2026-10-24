package com.astro.api.user.controller;

import com.astro.api.common.response.ApiResult;
import com.astro.api.user.dto.request.EmailVerificationRequestDto;
import com.astro.api.user.dto.request.UserActivationRequestDto;
import com.astro.api.user.dto.request.AccessKeyVerificationRequestDto;
import com.astro.api.user.dto.response.IdentificatedUserResponseDto;
import com.astro.api.user.service.UserService;
import com.astro.api.user.model.UserType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController("auth")
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("verify-email")
    @Operation(summary = "Verifica o e-mail de um usuário", description = "Consulta primeiro os administradores e depois os usuários pelo e-mail informado. Retorna userType ADMIN e userStatus null para administradores, que não possuem status no banco. Para os demais usuários, retorna o tipo e o status cadastrados.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuário identificado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado")
    })
    public ResponseEntity<ApiResult<IdentificatedUserResponseDto>> findUserTypeAndStatusByEmail(@RequestBody @Valid EmailVerificationRequestDto dto, HttpServletRequest request) {
        IdentificatedUserResponseDto user = userService.findTypeAndStatusByEmail(dto);

        return ResponseEntity.ok(
                ApiResult.success(
                        "Usuário identificado com sucesso",
                        user,
                        request.getRequestURI()
                )
        );
    }

    @PostMapping("activate")
    @Operation(summary = "Ativa um colaborador", description = "Atualiza o UID do Firebase do colaborador identificado pelo e-mail. A trigger do banco atualiza o status para ATIVO.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Colaborador ativado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Colaborador não encontrado")
    })
    public ResponseEntity<Void> activateCollaborator(@RequestBody @Valid UserActivationRequestDto dto) {
        userService.activateCollaborator(dto);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("verify-key")
    @Operation(summary = "Verifica uma chave de acesso", description = "Valida a chave de seis dígitos enviada por e-mail. O parâmetro opcional tipoConta assume COLABORADOR quando omitido ou vazio e exige um colaborador PRE_CADASTRADO. GESTOR_WORKSPACE valida o código de workspace no Redis sem exigir usuário cadastrado. O parâmetro não atribui perfil ao usuário.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Chave validada com sucesso"),
            @ApiResponse(responseCode = "403", description = "Chave incorreta ou ausente, ou colaborador não elegível"),
            @ApiResponse(responseCode = "400", description = "Corpo da requisição ou parâmetro tipoConta inválido; use COLABORADOR ou GESTOR_WORKSPACE")
    })
    public ResponseEntity<Void> verifyAccessKey(
            @RequestBody @Valid AccessKeyVerificationRequestDto dto,
            @RequestParam(name = "tipoConta", defaultValue = "COLABORADOR") UserType tipoConta) {
        if (tipoConta == UserType.GESTOR) {
            return ResponseEntity.badRequest().build();
        }

        if (!userService.verifyAccessKey(dto, tipoConta)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.noContent().build();
    }
}
