package com.astro.api.manager.controller;

import com.astro.api.auth.security.*;
import com.astro.api.auth.security.Role;
import com.astro.api.auth.service.*;
import com.astro.api.config.CorsConfig;
import com.astro.api.manager.service.ManagerService;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ManagerSecurityTest {
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    ManagerService service;
    FirebaseTokenService tokens;
    AuthorizationService authorization;

    @BeforeEach void open() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "astro.cors.allowed-origins=http://localhost");
        context.register(Config.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        service = context.getBean(ManagerService.class);
        tokens = context.getBean(FirebaseTokenService.class);
        authorization = context.getBean(AuthorizationService.class);
    }
    @AfterEach void close() { context.close(); }

    @Test void rejectsMissingOrInvalidAuthenticationAndUnauthorizedProfiles() throws Exception {
        when(tokens.verify("invalid")).thenThrow(mock(com.google.firebase.auth.FirebaseAuthException.class));
        mvc.perform(get("/managers")).andExpect(status().isForbidden());
        mvc.perform(get("/managers").header("Authorization", "Bearer invalid")).andExpect(status().isForbidden());
        FirebaseToken token = mock(FirebaseToken.class);
        when(tokens.verify("valid")).thenReturn(token); when(token.getUid()).thenReturn("uid");
        for (Role role : List.of(Role.COLABORADOR, Role.GESTOR, Role.ADMIN)) {
            when(authorization.getRole("uid")).thenReturn(Optional.of(role));
            mvc.perform(get("/managers").header("Authorization", "Bearer valid")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void permitsOnlyWorkspaceManager() throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        when(tokens.verify("valid")).thenReturn(token); when(token.getUid()).thenReturn("uid");
        when(authorization.getRole("uid")).thenReturn(Optional.of(Role.GESTOR_WORKSPACE));
        when(service.findAll(any())).thenReturn(List.of());
        mvc.perform(get("/managers").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }
    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfig.class, CorsConfig.class, FirebaseAuthenticationFilter.class, ManagerController.class})
    static class Config {
        @Bean ManagerService service() { return mock(ManagerService.class); }
        @Bean FirebaseTokenService tokens() { return mock(FirebaseTokenService.class); }
        @Bean AuthorizationService authorization() { return mock(AuthorizationService.class); }
    }
}
