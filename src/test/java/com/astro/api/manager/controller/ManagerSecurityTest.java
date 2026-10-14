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
            mvc.perform(get("/managers/1").header("Authorization", "Bearer valid")).andExpect(status().isForbidden());
            mvc.perform(post("/managers").header("Authorization", "Bearer valid").contentType("application/json")
                    .content("{\"collaboratorId\":4,\"status\":\"ATIVO\"}")).andExpect(status().isForbidden());
            mvc.perform(put("/managers/1").header("Authorization", "Bearer valid").contentType("application/json")
                    .content("{\"nome\":\"Gestor\",\"cpf\":\"52998224725\",\"email\":\"user@astro.com\",\"roleId\":1,\"unitId\":1,\"modalidade\":\"REMOTO\",\"status\":\"ATIVO\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/managers/1/status").header("Authorization", "Bearer valid").contentType("application/json")
                    .content("{\"status\":\"DESATIVADO\"}")).andExpect(status().isForbidden());
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
    @Test void theSameIssuedTokenUsesTheCurrentProfileAndLosesAccessAfterDeactivation() throws Exception {
        var authRepository = mock(com.astro.api.auth.repository.AuthRepository.class);
        var userService = mock(com.astro.api.user.service.UserService.class);
        var actualAuthorization = new AuthorizationService(authRepository, userService);
        var type = new java.util.concurrent.atomic.AtomicReference<>("COLABORADOR");
        var status = new java.util.concurrent.atomic.AtomicReference<>(com.astro.api.user.model.UserStatus.ATIVO);
        when(authRepository.findAccessLevel("target")).thenAnswer(call -> type.get());
        when(userService.findStatusByFirebaseUid("target")).thenAnswer(call -> Optional.of(status.get()));
        when(authorization.getRole("target")).thenAnswer(call -> actualAuthorization.getRole("target"));
        var token = mock(FirebaseToken.class); when(token.getUid()).thenReturn("target");
        when(tokens.verify("same-token")).thenReturn(token);
        mvc.perform(get("/manager-access").header("Authorization", "Bearer same-token")).andExpect(status().isForbidden());
        type.set("GESTOR");
        mvc.perform(get("/manager-access").header("Authorization", "Bearer same-token")).andExpect(status().isOk());
        status.set(com.astro.api.user.model.UserStatus.DESATIVADO);
        mvc.perform(get("/manager-access").header("Authorization", "Bearer same-token")).andExpect(status().isForbidden());
        verify(tokens, times(3)).verify("same-token");
        verify(authRepository, times(3)).findAccessLevel("target");
    }
    @org.springframework.web.bind.annotation.RestController
    static class ProbeController {
        @org.springframework.web.bind.annotation.GetMapping("/manager-access")
        @org.springframework.security.access.prepost.PreAuthorize("hasRole('GESTOR')")
        public String access() { return "ok"; }
    }
    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfig.class, CorsConfig.class, FirebaseAuthenticationFilter.class, ManagerController.class, ProbeController.class})
    static class Config {
        @Bean ManagerService service() { return mock(ManagerService.class); }
        @Bean FirebaseTokenService tokens() { return mock(FirebaseTokenService.class); }
        @Bean AuthorizationService authorization() { return mock(AuthorizationService.class); }
    }
}
