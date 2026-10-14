package com.astro.api.event.controller;

import com.astro.api.auth.security.*;
import com.astro.api.auth.security.Role;
import com.astro.api.auth.service.*;
import com.astro.api.config.CorsConfig;
import com.astro.api.event.service.EventService;
import com.astro.api.common.handler.GlobalExceptionHandler;
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

class EventSecurityTest {
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    EventService service;
    FirebaseTokenService tokens;
    AuthorizationService authorization;

    @BeforeEach void open() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "astro.cors.allowed-origins=http://localhost");
        context.register(Config.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        service = context.getBean(EventService.class);
        tokens = context.getBean(FirebaseTokenService.class);
        authorization = context.getBean(AuthorizationService.class);
    }
    @AfterEach void close() { context.close(); }

    @Test void requiresAuthenticationAndAdministrativeProfile() throws Exception {
        when(tokens.verify("invalid")).thenThrow(mock(com.google.firebase.auth.FirebaseAuthException.class));
        for (String path : List.of("/events/1", "/events/eligible-candidates", "/events?startDate=2026-10-01T00:00:00&endDate=2026-11-01T00:00:00")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        }
        var token = mock(FirebaseToken.class); when(token.getUid()).thenReturn("uid");
        when(tokens.verify("valid")).thenReturn(token);
        for (Role role : List.of(Role.COLABORADOR, Role.ADMIN)) {
            when(authorization.getRole("uid")).thenReturn(Optional.of(role));
            mvc.perform(get("/events/1").header("Authorization", "Bearer valid")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }
    @Test void permitsBothManagerProfilesAndResolvesCandidatesBeforeParameterizedRoute() throws Exception {
        var token = mock(FirebaseToken.class); when(token.getUid()).thenReturn("uid");
        when(tokens.verify("valid")).thenReturn(token);
        for (Role role : List.of(Role.GESTOR, Role.GESTOR_WORKSPACE)) {
            when(authorization.getRole("uid")).thenReturn(Optional.of(role));
            mvc.perform(get("/events/1").header("Authorization", "Bearer valid")).andExpect(status().isOk());
            mvc.perform(get("/events/eligible-candidates").header("Authorization", "Bearer valid")).andExpect(status().isOk());
        }
        verify(service, times(2)).findById(1L, "uid");
        verify(service, times(2)).findEligibleCandidates(null, "uid");
    }
    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfig.class, CorsConfig.class, FirebaseAuthenticationFilter.class, EventController.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean EventService service() { return mock(EventService.class); }
        @Bean FirebaseTokenService tokens() { return mock(FirebaseTokenService.class); }
        @Bean AuthorizationService authorization() { return mock(AuthorizationService.class); }
    }
}
