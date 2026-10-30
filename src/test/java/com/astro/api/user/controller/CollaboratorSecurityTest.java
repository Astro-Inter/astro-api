package com.astro.api.user.controller;

import com.astro.api.auth.security.*;
import com.astro.api.auth.service.AuthorizationService;
import com.astro.api.auth.service.FirebaseTokenService;
import com.astro.api.user.service.CollaboratorService;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.config.CorsConfig;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import jakarta.servlet.Filter;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CollaboratorSecurityTest {
    @Test
    void requiresValidatedFirebaseAuthenticationAndManagerAuthority() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Config.class);
            context.refresh();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
            FirebaseTokenService tokens = context.getBean(FirebaseTokenService.class);
            AuthorizationService authorization = context.getBean(AuthorizationService.class);
            CollaboratorService service = context.getBean(CollaboratorService.class);
            mvc.perform(post("/collaborators/2/access-key/resend")).andExpect(result -> assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403));
            mvc.perform(get("/collaborators")).andExpect(result -> assertTrue(result.getResponse().getStatus() >= 400));
            mvc.perform(patch("/collaborators/2/status").contentType("application/json").content("{\"status\":\"DESATIVADO\"}"))
                    .andExpect(result -> assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403));
            mvc.perform(get("/collaborators").header("Authorization", "invalid"))
                    .andExpect(result -> assertTrue(result.getResponse().getStatus() >= 400));
            when(tokens.verify("invalid-token")).thenThrow(mock(com.google.firebase.auth.FirebaseAuthException.class));
            mvc.perform(get("/collaborators").header("Authorization", "Bearer invalid-token"))
                    .andExpect(result -> assertTrue(result.getResponse().getStatus() >= 400));
            FirebaseToken token = mock(FirebaseToken.class);
            when(token.getUid()).thenReturn("manager");
            when(tokens.verify("valid")).thenReturn(token);
            for (Role role : List.of(Role.COLABORADOR, Role.ADMIN)) {
                when(authorization.getRole("manager")).thenReturn(Optional.of(role));
                mvc.perform(post("/collaborators/2/access-key/resend").header("Authorization", "Bearer valid"))
                        .andExpect(status().isForbidden());
                mvc.perform(get("/collaborators").header("Authorization", "Bearer valid"))
                        .andExpect(status().isForbidden());
                mvc.perform(get("/collaborators/2").header("Authorization", "Bearer valid"))
                        .andExpect(status().isForbidden());
                mvc.perform(post("/collaborators").header("Authorization", "Bearer valid")
                                .contentType("application/json").content(CollaboratorControllerTest.CREATE_JSON))
                        .andExpect(status().isForbidden());
                mvc.perform(put("/collaborators/2").header("Authorization", "Bearer valid")
                                .contentType("application/json").content(CollaboratorControllerTest.CREATE_JSON
                                        .replace("\"workModel\":\"HIBRIDO\"", "\"workModel\":\"HIBRIDO\",\"status\":\"ATIVO\"")))
                        .andExpect(status().isForbidden());
                mvc.perform(patch("/collaborators/2/status").header("Authorization", "Bearer valid")
                                .contentType("application/json").content("{\"status\":\"DESATIVADO\"}"))
                        .andExpect(status().isForbidden());
            }
            verifyNoInteractions(service);
            for (Role role : List.of(Role.GESTOR, Role.GESTOR_WORKSPACE)) {
                when(authorization.getRole("manager")).thenReturn(Optional.of(role));
                mvc.perform(get("/collaborators").header("Authorization", "Bearer valid"))
                        .andExpect(status().isOk());
                mvc.perform(post("/collaborators/2/access-key/resend").header("Authorization", "Bearer valid"))
                        .andExpect(status().isAccepted());
            }
        }
    }

    @Configuration
    @EnableWebSecurity
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    @Import({SecurityConfig.class, CorsConfig.class, CollaboratorController.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean FirebaseTokenService firebaseTokenService() { return mock(FirebaseTokenService.class); }
        @Bean AuthorizationService authorizationService() { return mock(AuthorizationService.class); }
        @Bean CollaboratorService collaboratorService() { return mock(CollaboratorService.class); }
        @Bean FirebaseAuthenticationFilter firebaseAuthenticationFilter(FirebaseTokenService tokens, AuthorizationService authorization) {
            return new FirebaseAuthenticationFilter(tokens, authorization);
        }
    }
}
