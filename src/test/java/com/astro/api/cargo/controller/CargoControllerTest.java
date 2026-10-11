package com.astro.api.cargo.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.cargo.dto.response.CargoResponse;
import com.astro.api.cargo.service.CargoService;
import com.astro.api.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.AccessDeniedException;
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

class CargoControllerTest {
    private final CargoService service = mock(CargoService.class);
    private final AuthenticatedUser user = new AuthenticatedUser("gestor", "gestor@astro.com", Role.GESTOR);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CargoController(service))
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
    void exposesPortugueseFieldsWithinTheStandardApiEnvelope() throws Exception {
        when(service.findAll("gestor", user)).thenReturn(List.of(new CargoResponse(12L, 3L, "Gerente", 25, "Ativo")));
        mvc.perform(get("/cargos").param("firebase_uid", "gestor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id_cargo").value(12))
                .andExpect(jsonPath("$.data[0].id_workspace").value(3))
                .andExpect(jsonPath("$.data[0].nome").value("Gerente"))
                .andExpect(jsonPath("$.data[0].quantidade_colaboradores").value(25));
    }

    @Test
    void resolvesNestedNrRoutesSeparately() throws Exception {
        when(service.findNrs("gestor", 12L, user)).thenReturn(List.of());
        when(service.findNrOptions("gestor", 12L, user)).thenReturn(List.of());
        mvc.perform(get("/cargos/12/nrs").param("firebase_uid", "gestor")).andExpect(status().isOk());
        mvc.perform(get("/cargos/12/nrs/opcoes").param("firebase_uid", "gestor")).andExpect(status().isOk());
        verify(service).findNrs("gestor", 12L, user);
        verify(service).findNrOptions("gestor", 12L, user);
    }

    @Test
    void returnsForbiddenForAnIdentityMismatch() throws Exception {
        when(service.findAll("outro", user)).thenThrow(new AccessDeniedException("UID inválido"));
        mvc.perform(get("/cargos").param("firebase_uid", "outro"))
                .andExpect(status().isForbidden());
    }
}
