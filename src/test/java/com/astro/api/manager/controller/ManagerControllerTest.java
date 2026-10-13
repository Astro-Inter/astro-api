package com.astro.api.manager.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.manager.dto.response.ManagerSummaryResponse;
import com.astro.api.manager.service.ManagerService;
import com.astro.api.user.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ManagerControllerTest {
    final ManagerService service = mock(ManagerService.class);
    final AuthenticatedUser principal = new AuthenticatedUser("owner", "owner@astro.com", Role.GESTOR_WORKSPACE);
    MockMvc mvc;

    @BeforeEach void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ManagerController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == AuthenticatedUser.class;
                    }
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                                  NativeWebRequest request, WebDataBinderFactory factory) {
                        return principal;
                    }
                }).build();
    }

    @Test void listsPortugueseJsonFieldsWithoutSensitiveData() throws Exception {
        when(service.findAll(principal)).thenReturn(List.of(new ManagerSummaryResponse(1L, "Gestor",
                "manager@astro.com", 10L, "Unidade", UserStatus.PRE_CADASTRADO)));
        mvc.perform(get("/managers").param("workspaceId", "999").param("firebase_uid", "attacker"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id_usuario").value(1))
                .andExpect(jsonPath("$.data[0].unidade_id").value(10))
                .andExpect(jsonPath("$.data[0].status").value("PRE_CADASTRADO"))
                .andExpect(jsonPath("$.data[0].cpf").doesNotExist())
                .andExpect(jsonPath("$.data[0].password").doesNotExist())
                .andExpect(jsonPath("$.data[0].firebase_uid").doesNotExist());
        verify(service).findAll(principal);
    }
}
