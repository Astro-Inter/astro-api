package com.astro.api.nrreview;

import static org.mockito.Mockito.*;

import static org.mockito.ArgumentMatchers.anyString;
import com.google.firebase.auth.FirebaseAuthException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.astro.api.auth.security.FirebaseAuthenticationFilter;
import com.astro.api.auth.security.Role;
import com.astro.api.auth.security.SecurityConfig;
import com.astro.api.auth.service.AuthorizationService;
import com.astro.api.auth.service.FirebaseTokenService;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.config.CorsConfig;
import com.astro.api.nrreview.controller.NrReviewController;
import com.astro.api.nrreview.service.NrReviewService;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import java.util.List;
import java.util.Optional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


class NrReviewSecurityTest {
    @Test void allEndpointsRequireValidatedAdminAndRejectMalformedWrites() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext()); context.register(Config.class); context.refresh();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
            FirebaseTokenService tokens = context.getBean(FirebaseTokenService.class);
            AuthorizationService roles = context.getBean(AuthorizationService.class);
            NrReviewService service = context.getBean(NrReviewService.class);
            mvc.perform(get("/admin/nrs/pendentes")).andExpect(status().isUnauthorized());
            mvc.perform(get("/admin/nrs/catalogo/20").header("Authorization", "invalid")).andExpect(status().isUnauthorized());
            when(tokens.verify("invalid")).thenThrow(mock(FirebaseAuthException.class));
            mvc.perform(get("/admin/nrs/pendentes").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
            FirebaseToken token = mock(FirebaseToken.class); when(token.getUid()).thenReturn("local-user"); when(tokens.verify("valid")).thenReturn(token);
            String content = "{\"nome\":\"Nome\",\"descricao\":\"Descrição\",\"objetivo\":\"Objetivo\",\"aplicabilidade\":\"Aplicabilidade\",\"usabilidade\":\"Empresa\",\"revogada\":false,\"tempo_reciclagem_meses\":12}";
            String revision = "a".repeat(64);
            String approval = "{\"revision\":\"" + revision + "\",\"approvedRevision\":\"absent\",\"corrections\":" + content + "}";
            String update = "{\"revision\":\"" + revision + "\",\"content\":" + content + "}";
            for (Role role : List.of(Role.GESTOR, Role.GESTOR_WORKSPACE, Role.COLABORADOR)) {
                when(roles.getRole("local-user")).thenReturn(Optional.of(role));
                for (String path : List.of("/pendentes", "/pendentes/20", "/catalogo", "/catalogo/20")) mvc.perform(get("/admin/nrs" + path).header("Authorization", "Bearer valid")).andExpect(status().isForbidden());
                mvc.perform(post("/admin/nrs/pendentes/20/aprovar").header("Authorization", "Bearer valid").contentType("application/json").content(approval)).andExpect(status().isForbidden());
                mvc.perform(put("/admin/nrs/catalogo/20").header("Authorization", "Bearer valid").contentType("application/json").content(update)).andExpect(status().isForbidden());
            }
            verifyNoInteractions(service);
            when(roles.getRole("local-user")).thenReturn(Optional.of(Role.ADMIN));
            for (String path : List.of("/pendentes", "/pendentes/20", "/catalogo", "/catalogo/20")) mvc.perform(get("/admin/nrs" + path).header("Authorization", "Bearer valid")).andExpect(status().isOk());
            mvc.perform(post("/admin/nrs/pendentes/20/aprovar").header("Authorization", "Bearer valid").contentType("application/json").content(approval)).andExpect(status().isOk());
            mvc.perform(put("/admin/nrs/catalogo/20").header("Authorization", "Bearer valid").contentType("application/json").content(update)).andExpect(status().isOk());
            clearInvocations(service);
            for (String body : List.of("{}", approval.replace("Empresa", "Outro"), approval.replace(":12", ":0"), approval.replace("Nome", ""))) mvc.perform(post("/admin/nrs/pendentes/20/aprovar").header("Authorization", "Bearer valid").contentType("application/json").content(body)).andExpect(status().isBadRequest());
            verifyNoInteractions(service);
        }
    }
    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfig.class, CorsConfig.class, NrReviewController.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean FirebaseTokenService firebaseTokenService() { return mock(FirebaseTokenService.class); }
        @Bean AuthorizationService authorizationService() { return mock(AuthorizationService.class); }
        @Bean NrReviewService nrReviewService() { return mock(NrReviewService.class); }
        @Bean FirebaseAuthenticationFilter firebaseAuthenticationFilter(FirebaseTokenService tokens, AuthorizationService roles) { return new FirebaseAuthenticationFilter(tokens, roles); }
    }
}
