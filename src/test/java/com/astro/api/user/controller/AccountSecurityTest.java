package com.astro.api.user.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.FirebaseAuthenticationFilter;
import com.astro.api.auth.security.Role;
import com.astro.api.auth.security.SecurityConfig;
import com.astro.api.auth.service.AuthorizationService;
import com.astro.api.auth.service.FirebaseTokenService;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.config.CorsConfig;
import com.astro.api.user.dto.response.AccountSummaryResponse;
import com.astro.api.user.service.AccountService;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountSecurityTest {
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    AccountService accounts;
    FirebaseTokenService tokens;
    AuthorizationService authorization;

    @BeforeEach void open() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "astro.cors.allowed-origins=http://localhost");
        context.register(Config.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        accounts = context.getBean(AccountService.class);
        tokens = context.getBean(FirebaseTokenService.class);
        authorization = context.getBean(AuthorizationService.class);
    }

    @AfterEach void close() { context.close(); }

    void authenticate(Role role) throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        when(tokens.verify("valid")).thenReturn(token);
        when(token.getUid()).thenReturn("own-uid");
        when(token.getEmail()).thenReturn("token@example.com");
        when(authorization.getRole("own-uid")).thenReturn(Optional.of(role));
    }

    @Test void rejectsMissingInvalidTokensAndOtherProfiles() throws Exception {
        when(tokens.verify("invalid")).thenThrow(mock(FirebaseAuthException.class));
        mvc.perform(get("/user/me/account-summary")).andExpect(status().isForbidden());
        mvc.perform(get("/user/me/account-summary").header("Authorization", "Bearer invalid"))
                .andExpect(status().isForbidden());
        for (Role role : new Role[]{Role.ADMIN, Role.COLABORADOR}) {
            authenticate(role);
            mvc.perform(get("/user/me/account-summary").header("Authorization", "Bearer valid"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(accounts);
    }

    @Test void exposesOnlyThreeSummaryFieldsForBothManagersAndIgnoresTargetIds() throws Exception {
        for (Role role : new Role[]{Role.GESTOR, Role.GESTOR_WORKSPACE}) {
            authenticate(role);
            var principal = new AuthenticatedUser("own-uid", "token@example.com", role);
            when(accounts.findSummary(principal)).thenReturn(new AccountSummaryResponse("Maria Rosa", "stored@example.com", null));
            mvc.perform(get("/user/me/account-summary?userId=999&firebaseUid=foreign-uid")
                            .header("Authorization", "Bearer valid"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", aMapWithSize(3)))
                    .andExpect(jsonPath("$.data.nome").value("Maria Rosa"))
                    .andExpect(jsonPath("$.data.email").value("stored@example.com"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("\"profilePhotoUrl\":null")))
                    .andExpect(jsonPath("$.path").value("/user/me/account-summary"));
            verify(accounts).findSummary(principal);
        }
    }

    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfig.class, CorsConfig.class, FirebaseAuthenticationFilter.class, AccountController.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean AccountService accounts() { return mock(AccountService.class); }
        @Bean FirebaseTokenService tokens() { return mock(FirebaseTokenService.class); }
        @Bean AuthorizationService authorization() { return mock(AuthorizationService.class); }
    }
}
