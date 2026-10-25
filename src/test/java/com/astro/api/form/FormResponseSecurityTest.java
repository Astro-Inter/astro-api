package com.astro.api.form;

import com.astro.api.auth.security.*;
import com.astro.api.auth.security.Role;
import com.astro.api.auth.service.*;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.config.CorsConfig;
import com.astro.api.form.controller.*;
import com.astro.api.form.service.*;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FormResponseSecurityTest {
    @Test
    void newRoutesDistinguishInvalidTokensFromMissingPermissionsAndAcceptAnswersWithoutGps() throws Exception {
        try (AnnotationConfigWebApplicationContext context = context()) {
            MockMvc mvc = mockMvc(context);
            FirebaseTokenService tokens = context.getBean(FirebaseTokenService.class);
            AuthorizationService authorization = context.getBean(AuthorizationService.class);
            FormResponseService responses = context.getBean(FormResponseService.class);
            FormService forms = context.getBean(FormService.class);
            String id = FormTestFixtures.formId(1);
            FirebaseToken token = mock(FirebaseToken.class);
            when(token.getUid()).thenReturn("manager");
            when(tokens.verify("valid")).thenReturn(token);
            when(tokens.verify("invalid")).thenThrow(mock(FirebaseAuthException.class));
            when(tokens.verify("")).thenThrow(new IllegalArgumentException("Empty token"));
            for (String header : List.of("", "Basic invalid", "Bearer invalid", "Bearer ")) {
                for (AbstractMockHttpServletRequestBuilder<?> request : requests(id)) {
                    if (!header.isEmpty()) request.header("Authorization", header);
                    mvc.perform(request).andExpect(status().isUnauthorized())
                            .andExpect(jsonPath("$.success").value(false))
                            .andExpect(jsonPath("$.message").value("Não autenticado"))
                            .andExpect(jsonPath("$.data").isEmpty())
                            .andExpect(jsonPath("$.errors[0]").value("Envie um token Firebase válido"))
                            .andExpect(jsonPath("$.path").isString());
                }
            }
            for (Optional<Role> role : List.of(Optional.of(Role.COLABORADOR), Optional.of(Role.ADMIN), Optional.<Role>empty())) {
                when(authorization.getRole("manager")).thenReturn(role);
                for (AbstractMockHttpServletRequestBuilder<?> request : requests(id)) {
                    mvc.perform(request.header("Authorization", "Bearer valid")).andExpect(status().isForbidden())
                            .andExpect(jsonPath("$.success").value(false))
                            .andExpect(jsonPath("$.message").value("Não autorizado"))
                            .andExpect(jsonPath("$.errors[0]").isString());
                }
            }
            verifyNoInteractions(responses, forms);
            for (Role role : List.of(Role.GESTOR, Role.GESTOR_WORKSPACE)) {
                when(authorization.getRole("manager")).thenReturn(Optional.of(role));
                for (AbstractMockHttpServletRequestBuilder<?> request : requests(id)) {
                    mvc.perform(request.header("Authorization", "Bearer valid")).andExpect(status().isOk());
                }
                mvc.perform(get("/forms/received").param("managerId", "999").param("workspaceId", "999")
                        .header("Authorization", "Bearer valid")).andExpect(status().isOk());
            }
            verifyNoInteractions(forms);
            mvc.perform(post("/forms/" + id + "/responses").header("Authorization", "Bearer valid")
                    .contentType("application/json").content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value("answers: A lista de respostas é obrigatória"));
            mvc.perform(post("/forms/" + id + "/review").header("Authorization", "Bearer valid")
                    .contentType("application/json").content("{\"status\":\"unknown\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void legacyFormRoutesKeepTheirExistingAuthenticationBehavior() throws Exception {
        try (AnnotationConfigWebApplicationContext context = context()) {
            MockMvc mvc = mockMvc(context);
            FirebaseTokenService tokens = context.getBean(FirebaseTokenService.class);
            AuthorizationService authorization = context.getBean(AuthorizationService.class);
            when(tokens.verify("invalid")).thenThrow(mock(FirebaseAuthException.class));
            FirebaseToken token = mock(FirebaseToken.class); when(token.getUid()).thenReturn("no-access");
            when(tokens.verify("valid")).thenReturn(token);
            when(authorization.getRole("no-access")).thenReturn(Optional.empty());
            for (String path : List.of("/forms", "/forms/recipients", "/forms/" + FormTestFixtures.formId(1), "/managers")) {
                for (String bearer : List.of("", "invalid", "valid")) {
                    AbstractMockHttpServletRequestBuilder<?> request = get(path);
                    if (!bearer.isEmpty()) request.header("Authorization", "Bearer " + bearer);
                    mvc.perform(request).andExpect(status().isForbidden());
                }
            }
        }
    }

    private AnnotationConfigWebApplicationContext context() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        return context;
    }

    private MockMvc mockMvc(AnnotationConfigWebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    private List<AbstractMockHttpServletRequestBuilder<?>> requests(String id) {
        String response = "{\"answers\":[]}";
        MockMultipartFile part = new MockMultipartFile(
                "response", "response.json", "application/json", response.getBytes(StandardCharsets.UTF_8));
        return List.of(get("/forms/received"), get("/forms/received/" + id), get("/forms/" + id + "/responses"),
                post("/forms/" + id + "/responses").contentType("application/json").content(response),
                multipart("/forms/" + id + "/responses").file(part),
                post("/forms/" + id + "/review").contentType("application/json").content("{\"status\":\"approved\"}"),
                get("/forms/" + id + "/images/image-id"));
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, CorsConfig.class, FormController.class, FormResponseController.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean
        FirebaseTokenService firebaseTokenService() {
            return mock(FirebaseTokenService.class);
        }

        @Bean
        AuthorizationService authorizationService() {
            return mock(AuthorizationService.class);
        }

        @Bean
        FormService formService() {
            return mock(FormService.class);
        }

        @Bean
        FormResponseService formResponseService() {
            return mock(FormResponseService.class);
        }

        @Bean
        FirebaseAuthenticationFilter firebaseAuthenticationFilter(FirebaseTokenService tokens, AuthorizationService authorization) {
            return new FirebaseAuthenticationFilter(tokens, authorization);
        }
    }
}
