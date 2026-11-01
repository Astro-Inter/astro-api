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
                "manager@astro.com", 10L, "Unidade", UserStatus.PRE_CADASTRADO,
                "https://r2.example.com/photo?signature=abc&expires=123", "G")));
        mvc.perform(get("/managers").param("workspaceId", "999").param("firebase_uid", "attacker"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].unitId").value(10))
                .andExpect(jsonPath("$.data[0].status").value("PRE_CADASTRADO"))
                .andExpect(jsonPath("$.data[0].profilePhotoUrl").value("https://r2.example.com/photo?signature=abc&expires=123"))
                .andExpect(jsonPath("$.data[0].avatarInitials").value("G"))
                .andExpect(jsonPath("$.data[0].cpf").doesNotExist())
                .andExpect(jsonPath("$.data[0].password").doesNotExist())
                .andExpect(jsonPath("$.data[0].firebase_uid").doesNotExist());
        verify(service).findAll(principal);
    }
    @Test void detailsExposeOnlyAdministrativeFieldsAndReturnStandardErrors() throws Exception {
        when(service.findById(1L, principal)).thenReturn(new com.astro.api.manager.dto.response.ManagerDetailsResponse(
                1L, "Gestor", "12345678900", "manager@astro.com", null, UserStatus.ATIVO,
                2L, "Cargo", 10L, "Unidade", 3L, null, "G"));
        mvc.perform(get("/managers/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cpf").value("12345678900"))
                .andExpect(jsonPath("$.data.roleId").value(2)).andExpect(jsonPath("$.data.workspaceId").value(3))
                .andExpect(jsonPath("$.data.profilePhotoUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.avatarInitials").value("G"))
                .andExpect(jsonPath("$.data.firebase_uid").doesNotExist()).andExpect(jsonPath("$.data.senha").doesNotExist());
        when(service.findById(99L, principal)).thenThrow(new com.astro.api.common.exception.ResourceNotFoundException("Gestor não encontrado"));
        mvc.perform(get("/managers/99")).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Gestor não encontrado"));
        mvc.perform(get("/managers/abc")).andExpect(status().isBadRequest());
    }
    @Test void promotionValidatesItsContractAndReturnsTheSameUserLocation() throws Exception {
        when(service.promote(any(), eq(principal))).thenReturn(new com.astro.api.manager.dto.response.ManagerDetailsResponse(
                4L, "Gestor", "12345678900", "user@astro.com", null, UserStatus.DESATIVADO, 2L, "Cargo", 10L, "Unidade", 3L,
                "https://r2.example.com/collaborator", "G"));
        mvc.perform(post("/managers").contentType("application/json").content("{\"collaboratorId\":4,\"status\":\"DESATIVADO\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/managers/4"))
                .andExpect(jsonPath("$.data.status").value("DESATIVADO"))
                .andExpect(jsonPath("$.data.profilePhotoUrl").value("https://r2.example.com/collaborator"))
                .andExpect(jsonPath("$.data.avatarInitials").value("G"));
        clearInvocations(service);
        for (String body : List.of("{}", "{\"collaboratorId\":0,\"status\":\"ATIVO\"}",
                "{\"collaboratorId\":4,\"status\":\"PRE_CADASTRADO\"}")) {
            mvc.perform(post("/managers").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
    @Test void putRequiresEveryEditableFieldAndUsesPortugueseValidationMessages() throws Exception {
        String valid = """
                {"name":"Gestor","cpf":"529.982.247-25","email":"manager@astro.com",
                 "roleId":2,"unitId":10,"workModel":"REMOTO","status":"ATIVO"}
                """;
        mvc.perform(put("/managers/1").contentType("application/json").content(valid)).andExpect(status().isOk());
        clearInvocations(service);
        mvc.perform(put("/managers/1").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("O nome é obrigatório"))))
                .andExpect(jsonPath("$.errors", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("A modalidade é obrigatória"))));
        for (String invalid : List.of(valid.replace("529.982.247-25", "11111111111"), valid.replace("manager@astro.com", "invalid"),
                valid.replace("\"roleId\":2", "\"roleId\":null"), valid.replace("\"unitId\":10", "\"unitId\":0"),
                valid.replace("\"REMOTO\"", "null"), valid.replace("\"ATIVO\"", "null"))) {
            mvc.perform(put("/managers/1").contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
    @Test void demotionNeedsNoBodyAndReturnsTheStandardEnvelopeAndErrors() throws Exception {
        when(service.demote(4L, principal)).thenReturn(new com.astro.api.manager.dto.response.ManagerDetailsResponse(
                4L, "Colaborador", "12345678900", "user@astro.com", null, UserStatus.DESATIVADO,
                2L, "Cargo", 10L, "Unidade", 3L, null, "C"));
        mvc.perform(patch("/managers/4/demote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Gestor despromovido a colaborador com sucesso"))
                .andExpect(jsonPath("$.path").value("/managers/4/demote"))
                .andExpect(jsonPath("$.data.id").value(4))
                .andExpect(jsonPath("$.data.status").value("DESATIVADO"))
                .andExpect(jsonPath("$.data.firebaseUid").doesNotExist());
        when(service.demote(99L, principal)).thenThrow(new com.astro.api.common.exception.ResourceNotFoundException("Gestor não encontrado"));
        mvc.perform(patch("/managers/99/demote")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        when(service.demote(0L, principal)).thenThrow(new com.astro.api.common.exception.BusinessException("O identificador do usuário deve ser positivo"));
        mvc.perform(patch("/managers/0/demote")).andExpect(status().isBadRequest());
        mvc.perform(patch("/managers/abc/demote")).andExpect(status().isBadRequest());
    }

    @Test void patchRequiresAnExplicitActiveOrInactiveStatus() throws Exception {
        mvc.perform(patch("/managers/1/status").contentType("application/json").content("{\"status\":\"DESATIVADO\"}"))
                .andExpect(status().isOk());
        verify(service).changeStatus(eq(1L), any(), eq(principal)); clearInvocations(service);
        for (String body : List.of("{}", "{\"status\":null}", "{\"status\":\"PRE_CADASTRADO\"}", "{\"status\":\"invalid\"}")) {
            mvc.perform(patch("/managers/1/status").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
