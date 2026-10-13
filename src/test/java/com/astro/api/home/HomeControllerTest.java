package com.astro.api.home;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.home.controller.HomeController;
import com.astro.api.home.repository.HomeRepository;
import com.astro.api.home.service.HomeService;
import com.astro.api.common.service.ViewedService;
import com.astro.api.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HomeControllerTest {
    private final HomeRepository repository = mock(HomeRepository.class);
    private final AuthenticatedUser user = new AuthenticatedUser("manager", "manager@astro.com", Role.GESTOR);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var service = new HomeService(repository, JsonMapper.builder().build(),
                mock(NotificationService.class), mock(ViewedService.class));
        mvc = MockMvcBuilders.standaloneSetup(new HomeController(service))
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
    void returnsDatabaseIndicatorsAsAnObjectUsingOnlyTheAuthenticatedIdentity() throws Exception {
        when(repository.loadHome("manager")).thenReturn("""
                {"nome_usuario":"Kirk Hammett","estatisticas":{
                  "quantidade_unidades":4,"quantidade_colaboradores":248,
                  "quantidade_requisitos":100,"quantidade_conformes":85,
                  "quantidade_proximos_vencimento":7,"quantidade_vencidos":5,
                  "quantidade_pendentes":3,"percentual_conformidade_geral":92.00,
                  "data_referencia":"2026-10-12"}}
                """);
        mvc.perform(get("/home/workspace").param("firebase_uid", "another-manager").param("workspace_id", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.path").value("/home/workspace"))
                .andExpect(jsonPath("$.data.nome_usuario").value("Kirk Hammett"))
                .andExpect(jsonPath("$.data.estatisticas.quantidade_unidades").value(4))
                .andExpect(jsonPath("$.data.estatisticas.quantidade_colaboradores").value(248))
                .andExpect(jsonPath("$.data.estatisticas.quantidade_requisitos").value(100))
                .andExpect(jsonPath("$.data.estatisticas.quantidade_conformes").value(85))
                .andExpect(jsonPath("$.data.estatisticas.quantidade_proximos_vencimento").value(7))
                .andExpect(jsonPath("$.data.estatisticas.quantidade_vencidos").value(5))
                .andExpect(jsonPath("$.data.estatisticas.quantidade_pendentes").value(3))
                .andExpect(jsonPath("$.data.estatisticas.percentual_conformidade_geral").value(92.0))
                .andExpect(jsonPath("$.data.estatisticas.data_referencia").value("2026-10-12"));
        verify(repository).loadHome("manager");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void preservesZeroIndicatorsForAnEmptyWorkspace() throws Exception {
        when(repository.loadHome("manager")).thenReturn("""
                {"nome_usuario":"Gestor","estatisticas":{
                  "quantidade_unidades":0,"quantidade_colaboradores":0,
                  "quantidade_requisitos":0,"quantidade_conformes":0,
                  "quantidade_proximos_vencimento":0,"quantidade_vencidos":0,
                  "quantidade_pendentes":0,"percentual_conformidade_geral":0.00,
                  "data_referencia":"2026-10-12"}}
                """);
        mvc.perform(get("/home/workspace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estatisticas.quantidade_requisitos").value(0))
                .andExpect(jsonPath("$.data.estatisticas.percentual_conformidade_geral").value(0.0));
    }

    @Test
    void returnsForbiddenWhenTheDatabaseDeniesWorkspaceAccess() throws Exception {
        mvc.perform(get("/home/workspace"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0]").value("Você não tem permissão para executar esta operação"));
    }

    @Test
    void preservesTheDatabaseUnavailableResponse() throws Exception {
        when(repository.loadHome("manager")).thenThrow(new DataAccessResourceFailureException("offline"));
        mvc.perform(get("/home/workspace"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }
}
