package com.astro.api.chat.controller;

import com.astro.api.auth.security.FirebaseAuthenticationFilter;
import com.astro.api.auth.security.Role;
import com.astro.api.auth.security.SecurityConfig;
import com.astro.api.auth.service.AuthorizationService;
import com.astro.api.auth.service.FirebaseTokenService;
import com.astro.api.chat.service.ChatService;
import com.astro.api.chat.service.ChatContactService;
import com.astro.api.chat.dto.response.ChatMessagePageResponse;
import com.astro.api.common.handler.GlobalExceptionHandler;
import com.astro.api.config.CorsConfig;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatSecurityIntegrationTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private ChatService chatService;
    private FirebaseTokenService tokens;
    private AuthorizationService authorization;

    @BeforeEach
    void open() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "astro.cors.allowed-origins=http://localhost");
        context.register(Config.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        chatService = context.getBean(ChatService.class);
        tokens = context.getBean(FirebaseTokenService.class);
        authorization = context.getBean(AuthorizationService.class);
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void requiresValidFirebaseAuthentication() throws Exception {
        when(tokens.verify("invalid")).thenThrow(mock(FirebaseAuthException.class));
        String conversationId = "11111111-2222-4333-8444-555555555555";

        mvc.perform(get("/chats")).andExpect(status().isUnauthorized());
        mvc.perform(get("/chats").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/chats/{conversationId}/messages", conversationId))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(chatService);
    }

    @Test
    void allowsCollaboratorRolesAndRejectsAdminRole() throws Exception {
        String conversationId = "11111111-2222-4333-8444-555555555555";
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("uid");
        when(tokens.verify("valid")).thenReturn(token);
        when(chatService.findChats(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(chatService.findMessages(any(), eq(conversationId), eq(30), isNull()))
                .thenReturn(new ChatMessagePageResponse(List.of(), null, false));

        for (Role role : List.of(Role.COLABORADOR, Role.GESTOR, Role.GESTOR_WORKSPACE)) {
            when(authorization.getRole("uid")).thenReturn(Optional.of(role));
            mvc.perform(get("/chats").header("Authorization", "Bearer valid"))
                    .andExpect(status().isOk());
            mvc.perform(get("/chats/{conversationId}/messages", conversationId)
                            .header("Authorization", "Bearer valid"))
                    .andExpect(status().isOk());
        }

        when(authorization.getRole("uid")).thenReturn(Optional.of(Role.ADMIN));
        mvc.perform(get("/chats").header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/chats/{conversationId}/messages", conversationId)
                        .header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden());
        verify(chatService, org.mockito.Mockito.times(3)).findChats(org.mockito.ArgumentMatchers.any());
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, CorsConfig.class, FirebaseAuthenticationFilter.class,
            ChatController.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean ChatService chatService() { return mock(ChatService.class); }
        @Bean ChatContactService chatContactService() { return mock(ChatContactService.class); }
        @Bean FirebaseTokenService tokens() { return mock(FirebaseTokenService.class); }
        @Bean AuthorizationService authorization() { return mock(AuthorizationService.class); }
    }
}
