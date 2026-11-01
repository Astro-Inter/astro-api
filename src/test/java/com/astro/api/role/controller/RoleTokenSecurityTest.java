package com.astro.api.role.controller;

import com.astro.api.auth.security.*;
import com.astro.api.auth.security.Role;
import com.astro.api.auth.service.AuthorizationService;
import com.astro.api.auth.service.FirebaseTokenService;
import com.astro.api.config.CorsConfig;
import com.astro.api.role.service.RoleService;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.Filter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoleTokenSecurityTest {
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    RoleService service;
    FirebaseTokenService tokens;
    AuthorizationService authorization;

    @BeforeEach void open() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "astro.cors.allowed-origins=http://localhost");
        context.register(Config.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        service = context.getBean(RoleService.class);
        tokens = context.getBean(FirebaseTokenService.class);
        authorization = context.getBean(AuthorizationService.class);
    }

    @AfterEach void close() { context.close(); }

    List<MockHttpServletRequestBuilder> requests() {
        String role = "{\"name\":\"Gerente\",\"status\":\"Ativo\"}";
        return List.of(get("/roles"), get("/roles/12/nrs"), get("/roles/12/nrs/options"),
                post("/roles").contentType("application/json").content(role),
                put("/roles/12").contentType("application/json").content(role),
                patch("/roles/12/status"),
                post("/roles/12/nrs").contentType("application/json").content("{\"nrs\":[]}"));
    }

    @Test void rejectsMissingInvalidAndUnauthorizedAuthenticationOnAllRoutes() throws Exception {
        when(tokens.verify("invalid")).thenThrow(mock(com.google.firebase.auth.FirebaseAuthException.class));
        for (MockHttpServletRequestBuilder request : requests()) mvc.perform(request).andExpect(status().isForbidden());
        for (MockHttpServletRequestBuilder request : requests()) {
            mvc.perform(request.header("Authorization", "Bearer invalid")).andExpect(status().isForbidden());
        }
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("authenticated");
        when(tokens.verify("valid")).thenReturn(token);
        for (Role role : List.of(Role.COLABORADOR, Role.ADMIN)) {
            when(authorization.getRole("authenticated")).thenReturn(Optional.of(role));
            for (MockHttpServletRequestBuilder request : requests()) {
                mvc.perform(request.header("Authorization", "Bearer valid")).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(service);
    }

    @Test void acceptsBothManagerProfilesWithoutAnyClientUid() throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("authenticated");
        when(token.getEmail()).thenReturn("manager@example.test");
        when(tokens.verify("valid")).thenReturn(token);
        for (Role role : List.of(Role.GESTOR, Role.GESTOR_WORKSPACE)) {
            when(authorization.getRole("authenticated")).thenReturn(Optional.of(role));
            for (MockHttpServletRequestBuilder request : requests()) {
                mvc.perform(request.header("Authorization", "Bearer valid")).andExpect(status().is2xxSuccessful());
            }
            AuthenticatedUser principal = new AuthenticatedUser("authenticated", "manager@example.test", role);
            verify(service).findAll(principal);
            verify(service).findNrs(12L, principal);
            verify(service).findNrOptions(12L, principal);
            verify(service).create(any(), eq(principal));
            verify(service).update(eq(12L), any(), eq(principal));
            verify(service).toggleStatus(12L, principal);
            verify(service).updateNrs(eq(12L), any(), eq(principal));
        }
    }

    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfig.class, CorsConfig.class, FirebaseAuthenticationFilter.class, RoleController.class})
    static class Config {
        @Bean RoleService service() { return mock(RoleService.class); }
        @Bean FirebaseTokenService tokens() { return mock(FirebaseTokenService.class); }
        @Bean AuthorizationService authorization() { return mock(AuthorizationService.class); }
    }
}
