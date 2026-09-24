package com.astro.api.user.controller;

import com.astro.api.common.response.ApiResult;
import com.astro.api.user.dto.request.EmailVerificationRequestDto;
import com.astro.api.user.dto.request.UserActivationRequestDto;
import com.astro.api.user.dto.response.IdentificatedUserResponseDto;
import com.astro.api.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("auth")
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("verify-email")
    @Operation(summary = "Verifica o e-mail de um usuário", description = "Identifica o usuário correspondente ao e-mail informado e retorna seu tipo e status.")
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
}
