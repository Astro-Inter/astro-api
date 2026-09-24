package com.astro.api.controller;

import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.user.controller.AuthController;
import com.astro.api.user.dto.request.EmailVerificationRequestDto;
import com.astro.api.user.dto.response.IdentificatedUserResponseDto;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private UserService userService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnUserTypeAndStatusWhenEmailExists() throws Exception {
        EmailVerificationRequestDto requestDto = new EmailVerificationRequestDto("gestor@astro.com");

        when(userService.findTypeAndStatusByEmail(requestDto))
                .thenReturn(new IdentificatedUserResponseDto(UserType.GESTOR, UserStatus.ATIVO));

        mockMvc.perform(post("/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"gestor@astro.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Usuário identificado com sucesso"))
                .andExpect(jsonPath("$.data.userType").value("GESTOR"))
                .andExpect(jsonPath("$.data.userStatus").value("ATIVO"))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(jsonPath("$.path").value("/verify-email"));

        verify(userService).findTypeAndStatusByEmail(requestDto);
    }

    @Test
    void shouldReturnBadRequestWhenEmailIsBlank() throws Exception {
        mockMvc.perform(post("/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Erro de validação"))
                .andExpect(jsonPath("$.errors[0]", containsString("email:")))
                .andExpect(jsonPath("$.path").value("/verify-email"));

        verifyNoInteractions(userService);
    }

    @Test
    void shouldReturnBadRequestWhenRequestBodyIsMalformed() throws Exception {
        mockMvc.perform(post("/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Corpo da requisição inválido"))
                .andExpect(jsonPath("$.errors[0]").value("Verifique o formato dos dados enviados"))
                .andExpect(jsonPath("$.path").value("/verify-email"));

        verifyNoInteractions(userService);
    }

    @Test
    void shouldReturnNotFoundWhenEmailDoesNotBelongToAnyUser() throws Exception {
        EmailVerificationRequestDto requestDto = new EmailVerificationRequestDto("inexistente@astro.com");

        when(userService.findTypeAndStatusByEmail(requestDto))
                .thenThrow(new ResourceNotFoundException("Usuário não encontrado"));

        mockMvc.perform(post("/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"inexistente@astro.com\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Usuário não encontrado"))
                .andExpect(jsonPath("$.errors[0]").value("Usuário não encontrado"))
                .andExpect(jsonPath("$.path").value("/verify-email"));

        verify(userService).findTypeAndStatusByEmail(requestDto);
    }

    @Test
    void shouldReturnBadRequestWhenEmailFormatIsInvalid() throws Exception {
        mockMvc.perform(post("/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"email-invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Erro de validação"))
                .andExpect(jsonPath("$.errors[0]", containsString("email:")))
                .andExpect(jsonPath("$.path").value("/verify-email"));

        verifyNoInteractions(userService);
    }
}
