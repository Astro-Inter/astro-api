package com.astro.api.form;

import com.astro.api.auth.security.*;
import com.astro.api.auth.security.Role;
import com.astro.api.auth.service.AuthorizationService;
import com.astro.api.auth.service.FirebaseTokenService;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.config.CorsConfig;
import com.astro.api.form.controller.FormController;
import com.astro.api.form.service.FormService;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import jakarta.servlet.Filter;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FormSecurityTest {
    @Test void requiresFirebaseAndManagerAuthority() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext()); context.register(Config.class); context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
            var tokens = context.getBean(FirebaseTokenService.class);
            var authorization = context.getBean(AuthorizationService.class);
            var service = context.getBean(FormService.class);
            mvc.perform(get("/forms")).andExpect(result -> assertTrue(result.getResponse().getStatus() >= 400));
            when(tokens.verify("invalid")).thenThrow(mock(com.google.firebase.auth.FirebaseAuthException.class));
            mvc.perform(get("/forms").header("Authorization", "Bearer invalid"))
                    .andExpect(result -> assertTrue(result.getResponse().getStatus() >= 400));
            FirebaseToken token = mock(FirebaseToken.class); when(token.getUid()).thenReturn("manager");
            when(tokens.verify("valid")).thenReturn(token);
            for (Role role : List.of(Role.COLABORADOR, Role.ADMIN)) {
                when(authorization.getRole("manager")).thenReturn(Optional.of(role));
                mvc.perform(get("/forms").header("Authorization", "Bearer valid")).andExpect(status().isForbidden());
                mvc.perform(get("/forms/1").header("Authorization", "Bearer valid")).andExpect(status().isForbidden());
                mvc.perform(post("/forms").header("Authorization", "Bearer valid")
                        .contentType("application/json").content("{\"status\":\"RASCUNHO\"}")).andExpect(status().isForbidden());
                mvc.perform(put("/forms/1").header("Authorization", "Bearer valid")
                        .contentType("application/json").content("{}")).andExpect(status().isForbidden());
            }
            verifyNoInteractions(service);
            for (Role role : List.of(Role.GESTOR, Role.GESTOR_WORKSPACE)) {
                when(authorization.getRole("manager")).thenReturn(Optional.of(role));
                mvc.perform(get("/forms").header("Authorization", "Bearer valid")).andExpect(status().isOk());
            }
        }
    }

    @Configuration
    @EnableWebSecurity
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    @Import({SecurityConfig.class, CorsConfig.class, FormController.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean FirebaseTokenService firebaseTokenService() { return mock(FirebaseTokenService.class); }
        @Bean AuthorizationService authorizationService() { return mock(AuthorizationService.class); }
        @Bean FormService formService() { return mock(FormService.class); }
        @Bean FirebaseAuthenticationFilter firebaseAuthenticationFilter(FirebaseTokenService tokens, AuthorizationService authorization) {
            return new FirebaseAuthenticationFilter(tokens, authorization);
        }
    }
}
