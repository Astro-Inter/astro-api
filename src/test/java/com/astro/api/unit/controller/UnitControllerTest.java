package com.astro.api.unit.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.unit.dto.response.UnitResponse;
import com.astro.api.unit.dto.response.UnitNrResponse;
import com.astro.api.unit.dto.response.UnitNrOptionResponse;
import com.astro.api.unit.service.UnitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UnitControllerTest {
    private final UnitService service = mock(UnitService.class);
    private final AuthenticatedUser user = new AuthenticatedUser("manager", "manager@astro.com", Role.GESTOR);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new UnitController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == AuthenticatedUser.class;
                    }
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                                  NativeWebRequest request, WebDataBinderFactory factory) {
                        return user;
                    }
                }).build();
    }

    @Test
    void listsUnitsWithoutAClientUidAndMapsAllDatabaseFields() throws Exception {
        when(service.findAll(user)).thenReturn(List.of(new UnitResponse(12L, 3L, "Matriz", "Ativo", 25,
                "01234567", "Rua A", "São Paulo", "Centro", "SP", "Sala 1")));
        mvc.perform(get("/units").param("firebase_uid", "untrusted"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id_unidade").value(12))
                .andExpect(jsonPath("$.data[0].id_workspace").value(3))
                .andExpect(jsonPath("$.data[0].nome").value("Matriz"))
                .andExpect(jsonPath("$.data[0].status").value("Ativo"))
                .andExpect(jsonPath("$.data[0].quantidade_colaboradores").value(25))
                .andExpect(jsonPath("$.data[0].cep").value("01234567"))
                .andExpect(jsonPath("$.data[0].rua").value("Rua A"))
                .andExpect(jsonPath("$.data[0].cidade").value("São Paulo"))
                .andExpect(jsonPath("$.data[0].bairro").value("Centro"))
                .andExpect(jsonPath("$.data[0].estado").value("SP"))
                .andExpect(jsonPath("$.data[0].complemento").value("Sala 1"));
        verify(service).findAll(user);
    }

    @Test
    void togglesStatusWithoutABodyAndPreservesFalse() throws Exception {
        when(service.toggleStatus(12L, user)).thenReturn(false);
        mvc.perform(patch("/units/12/status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(false));
        verify(service).toggleStatus(12L, user);
    }

    @Test
    void distinguishesAssociatedNrsFromAvailableOptions() throws Exception {
        when(service.findNrs(12L, user)).thenReturn(List.of(new UnitNrResponse(17, "Ergonomia")));
        when(service.findNrOptions(12L, user)).thenReturn(List.of(new UnitNrOptionResponse(1, "Disposições gerais", false)));
        mvc.perform(get("/units/12/nrs/associated"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].nr").value(17))
                .andExpect(jsonPath("$.data[0].descricao").value("Ergonomia"));
        mvc.perform(get("/units/12/nrs"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].nr").value(1))
                .andExpect(jsonPath("$.data[0].ativo").value(false));
    }

    @Test
    void acceptsTheRawArrayAndAnEmptyPatch() throws Exception {
        for (String body : List.of("[]", "[{\"nr\":17,\"ativo\":false}]")) {
            mvc.perform(post("/units/12/nrs").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
        }
        verify(service, times(2)).updateNrs(eq(12L), any(), eq(user));
    }

    @Test
    void rejectsNonArraysAndCoercedScalarTypes() throws Exception {
        for (String body : List.of("{}", "null", "{\"nrs\":[]}", "[1]", "[[]]",
                "[{\"nr\":\"1\",\"ativo\":true}]", "[{\"nr\":1.0,\"ativo\":true}]",
                "[{\"nr\":2147483648,\"ativo\":true}]", "[{\"nr\":1,\"ativo\":\"true\"}]",
                "[{\"nr\":1,\"ativo\":1}]")) {
            mvc.perform(post("/units/12/nrs").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void resolvesResourceBundleMessagesForNestedValidation() throws Exception {
        assertValidationError("[null]", "Os itens da lista de NRs não podem ser nulos");
        assertValidationError("[{}]", "O código da NR é obrigatório");
        assertValidationError("[{\"nr\":0,\"ativo\":true}]", "O código da NR deve ser um inteiro positivo");
        assertValidationError("[{\"nr\":1}]", "Informe se a NR deve ser vinculada à unidade");
        verifyNoInteractions(service);
    }

    @Test
    void exposesStandardHttpErrorsForDatabaseRejections() throws Exception {
        when(service.toggleStatus(12L, user)).thenThrow(new AccessDeniedException("Sem acesso"));
        mvc.perform(patch("/units/12/status")).andExpect(status().isForbidden());
        doThrow(new ResourceNotFoundException("Unidade não encontrada")).when(service).toggleStatus(12L, user);
        mvc.perform(patch("/units/12/status")).andExpect(status().isNotFound());
        doThrow(new BusinessException("NR inválida")).when(service).updateNrs(eq(12L), any(), eq(user));
        mvc.perform(post("/units/12/nrs").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"nr\":999,\"ativo\":true}]"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("NR inválida"));
    }

    @Test
    void rejectsMalformedIdsWithoutCallingTheService() throws Exception {
        mvc.perform(get("/units/abc/nrs")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private void assertValidationError(String body, String message) throws Exception {
        mvc.perform(post("/units/12/nrs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(containsString(message))));
    }
}
