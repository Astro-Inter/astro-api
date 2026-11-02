package com.astro.api.auth.security;

import com.astro.api.auth.service.AuthorizationService;
import com.astro.api.auth.service.FirebaseTokenService;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.CannotCreateTransactionException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FirebaseAuthenticationFilterTest {
    private final FirebaseTokenService tokens = mock(FirebaseTokenService.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final FirebaseAuthenticationFilter filter = new FirebaseAuthenticationFilter(tokens, authorization);
    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/me");

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void cleanContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void registersVerifiedUidForEveryEligibleRole(Role role) throws Exception {
        authenticate(role);
        request.setParameter("firebase_uid", "forged-uid");

        filter.doFilter(request, response, chain);

        verify(authorization).registerDailyAccess("verified-uid");
        verify(chain).doFilter(request, response);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(authentication.isAuthenticated());
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        assertEquals("verified-uid", principal.firebaseUid());
    }

    @Test
    void doesNotRegisterUnauthenticatedRequests() throws Exception {
        filter.doFilter(request, response, chain);

        verifyNoInteractions(tokens, authorization);
        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotRegisterInvalidTokens() throws Exception {
        request.addHeader("Authorization", "Bearer invalid-token");
        when(tokens.verify("invalid-token")).thenThrow(mock(FirebaseAuthException.class));

        filter.doFilter(request, response, chain);

        verifyNoInteractions(authorization);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotRegisterIneligibleIdentityOnOrdinaryRoutes() throws Exception {
        authenticate(null);

        filter.doFilter(request, response, chain);

        verify(authorization, never()).registerDailyAccess(anyString());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void registersAuthenticatedFormIdentityWithoutRole() throws Exception {
        authenticate(null);
        request.setRequestURI("/forms/received");

        filter.doFilter(request, response, chain);

        verify(authorization).registerDailyAccess("verified-uid");
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void continuesWhenDailyAccessDatabaseWriteFails() throws Exception {
        authenticate(Role.COLABORADOR);
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(authorization).registerDailyAccess("verified-uid");

        filter.doFilter(request, response, chain);

        assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
        verify(chain).doFilter(request, response);
    }

    @Test
    void continuesWhenDailyAccessTransactionCannotStart() throws Exception {
        authenticate(Role.ADMIN);
        doThrow(new CannotCreateTransactionException("connection unavailable"))
                .when(authorization).registerDailyAccess("verified-uid");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void preservesDownstreamFailures() throws Exception {
        authenticate(Role.GESTOR);
        ServletException failure = new ServletException("controller failure");
        doThrow(failure).when(chain).doFilter(request, response);

        ServletException thrown = assertThrows(ServletException.class, () -> filter.doFilter(request, response, chain));

        assertSame(failure, thrown);
        verify(authorization).registerDailyAccess("verified-uid");
    }

    private void authenticate(Role role) throws FirebaseAuthException {
        request.addHeader("Authorization", "Bearer valid-token");
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("verified-uid");
        when(tokens.verify("valid-token")).thenReturn(token);
        when(authorization.getRole("verified-uid")).thenReturn(Optional.ofNullable(role));
    }
}
