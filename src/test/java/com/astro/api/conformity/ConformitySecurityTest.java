package com.astro.api.conformity;

import com.astro.api.auth.security.*;
import com.astro.api.auth.service.AuthorizationService;
import com.astro.api.auth.service.FirebaseTokenService;
import com.astro.api.conformity.service.ConformityService;
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

class ConformitySecurityTest {
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
            ConformityService service = context.getBean(ConformityService.class);
            mvc.perform(get("/conformities/collaborators")).andExpect(result -> assertTrue(result.getResponse().getStatus() >= 400));
            mvc.perform(patch("/conformities/collaborators/2/status").contentType("application/json").content("{\"status\":\"DESATIVADO\"}"))
                    .andExpect(result -> assertTrue(result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403));
            mvc.perform(get("/conformities/collaborators").header("Authorization", "invalid"))
                    .andExpect(result -> assertTrue(result.getResponse().getStatus() >= 400));
            when(tokens.verify("invalid-token")).thenThrow(mock(com.google.firebase.auth.FirebaseAuthException.class));
            mvc.perform(get("/conformities/collaborators").header("Authorization", "Bearer invalid-token"))
                    .andExpect(result -> assertTrue(result.getResponse().getStatus() >= 400));
            FirebaseToken token = mock(FirebaseToken.class);
            when(token.getUid()).thenReturn("manager");
            when(tokens.verify("valid")).thenReturn(token);
            for (Role role : List.of(Role.COLABORADOR, Role.ADMIN)) {
                when(authorization.getRole("manager")).thenReturn(Optional.of(role));
                mvc.perform(get("/conformities/collaborators").header("Authorization", "Bearer valid"))
                        .andExpect(status().isForbidden());
                mvc.perform(get("/conformities/collaborators/2").header("Authorization", "Bearer valid"))
                        .andExpect(status().isForbidden());
                mvc.perform(post("/conformities/collaborators").header("Authorization", "Bearer valid")
                                .contentType("application/json").content("{\"collaboratorId\":2,\"nrId\":10}"))
                        .andExpect(status().isForbidden());
                mvc.perform(put("/conformities/collaborators/2").header("Authorization", "Bearer valid")
                                .contentType("application/json").content("{\"nrId\":10,\"expiresAt\":null}"))
                        .andExpect(status().isForbidden());
                mvc.perform(patch("/conformities/collaborators/2/applicability").header("Authorization", "Bearer valid")
                                .contentType("application/json").content("{\"applicable\":false}"))
                        .andExpect(status().isForbidden());
                mvc.perform(multipart("/conformities/collaborators/import")
                                .file(new org.springframework.mock.web.MockMultipartFile("file", "test.xlsx", "application/octet-stream", new byte[]{1}))
                                .header("Authorization", "Bearer valid"))
                        .andExpect(status().isForbidden());
            }
            verifyNoInteractions(service);
            com.astro.api.conformity.service.UnitConformityService unitService = context.getBean(com.astro.api.conformity.service.UnitConformityService.class);
            mvc.perform(get("/conformities/units")).andExpect(status().isForbidden());
            for (Role role : List.of(Role.COLABORADOR, Role.ADMIN)) {
                when(authorization.getRole("manager")).thenReturn(Optional.of(role));
                mvc.perform(get("/conformities/units").header("Authorization", "Bearer valid"))
                        .andExpect(status().isForbidden());
                mvc.perform(get("/conformities/units/2").header("Authorization", "Bearer valid"))
                        .andExpect(status().isForbidden());
                mvc.perform(post("/conformities/units").header("Authorization", "Bearer valid")
                        .contentType("application/json").content("{\"unitId\":2,\"nrId\":10}"))
                        .andExpect(status().isForbidden());
                mvc.perform(put("/conformities/units/2").header("Authorization", "Bearer valid")
                        .contentType("application/json").content("{\"nrId\":10,\"expiresAt\":null}"))
                        .andExpect(status().isForbidden());
                mvc.perform(patch("/conformities/units/2/applicability").header("Authorization", "Bearer valid")
                        .contentType("application/json").content("{\"applicable\":false}"))
                        .andExpect(status().isForbidden());
            }
            verifyNoInteractions(unitService);
            for (Role role : List.of(Role.GESTOR, Role.GESTOR_WORKSPACE)) {
                when(authorization.getRole("manager")).thenReturn(Optional.of(role));
                mvc.perform(get("/conformities/collaborators").header("Authorization", "Bearer valid"))
                        .andExpect(status().isOk());
                mvc.perform(get("/conformities/units").header("Authorization", "Bearer valid"))
                        .andExpect(status().isOk());
            }
        }
    }

    @Configuration
    @EnableWebSecurity
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    @Import({SecurityConfig.class, CorsConfig.class, com.astro.api.conformity.controller.CollaboratorConformityController.class, com.astro.api.conformity.controller.UnitConformityController.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean com.astro.api.conformity.service.ConformityImportService conformityImportService() { return mock(com.astro.api.conformity.service.ConformityImportService.class); }
        @Bean FirebaseTokenService firebaseTokenService() { return mock(FirebaseTokenService.class); }
        @Bean AuthorizationService authorizationService() { return mock(AuthorizationService.class); }
        @Bean com.astro.api.conformity.service.UnitConformityService unitConformityService() { return mock(com.astro.api.conformity.service.UnitConformityService.class); }
        @Bean ConformityService conformityService() { return mock(ConformityService.class); }
        @Bean FirebaseAuthenticationFilter firebaseAuthenticationFilter(FirebaseTokenService tokens, AuthorizationService authorization) {
            return new FirebaseAuthenticationFilter(tokens, authorization);
        }
    }
}
