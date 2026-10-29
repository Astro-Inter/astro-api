package com.astro.api.user.controller;

import com.astro.api.user.swagger.AuthSwagger;

import com.astro.api.common.response.ApiResult;
import com.astro.api.user.dto.request.EmailVerificationRequestDto;
import com.astro.api.user.dto.request.UserActivationRequestDto;
import com.astro.api.user.dto.request.AccessKeyVerificationRequestDto;
import com.astro.api.user.dto.response.IdentificatedUserResponseDto;
import com.astro.api.user.service.UserService;
import com.astro.api.user.model.UserType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController("auth")
public class AuthController implements AuthSwagger {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("verify-email")
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
    public ResponseEntity<Void> activateCollaborator(@RequestBody @Valid UserActivationRequestDto dto) {
        userService.activateCollaborator(dto);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("verify-key")
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
