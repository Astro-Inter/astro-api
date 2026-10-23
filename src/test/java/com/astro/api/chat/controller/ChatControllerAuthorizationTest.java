package com.astro.api.chat.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.chat.mapper.ChatContactMapper;
import com.astro.api.chat.service.ChatContactService;
import com.astro.api.user.repository.UserRepository;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatControllerAuthorizationTest {
    @Test
    void deniesAdminAndUnauthenticatedPrincipals() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class)) {
            ChatController controller = context.getBean(ChatController.class);
            SecurityContextHolder.getContext().setAuthentication(authentication("ADMIN"));
            assertThrows(AccessDeniedException.class,
                    () -> controller.findContacts(principal(Role.ADMIN), new MockHttpServletRequest()));

            SecurityContextHolder.clearContext();
            assertThrows(AuthenticationCredentialsNotFoundException.class,
                    () -> controller.findContacts(principal(Role.COLABORADOR), new MockHttpServletRequest()));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private UsernamePasswordAuthenticationToken authentication(String role) {
        return new UsernamePasswordAuthenticationToken("user", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private AuthenticatedUser principal(Role role) {
        return new AuthenticatedUser("uid", "user@astro.test", role);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean ChatContactService chatContactService() {
            UserRepository unusedRepository = (UserRepository) Proxy.newProxyInstance(
                    UserRepository.class.getClassLoader(), new Class<?>[]{UserRepository.class},
                    (proxy, method, args) -> { throw new UnsupportedOperationException(method.getName()); });
            return new ChatContactService(unusedRepository, new ChatContactMapper());
        }
        @Bean ChatController chatController(ChatContactService service) { return new ChatController(service); }
    }
}
