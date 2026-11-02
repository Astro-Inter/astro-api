package com.astro.api.auth.security;

import com.astro.api.auth.service.AuthorizationService;
import com.astro.api.auth.service.FirebaseTokenService;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);

    private final FirebaseTokenService firebaseTokenService;
    private final AuthorizationService authorizationService;

    public FirebaseAuthenticationFilter(FirebaseTokenService firebaseTokenService, AuthorizationService authorizationService) {

        this.firebaseTokenService = firebaseTokenService;
        this.authorizationService = authorizationService;
    }

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String idToken = authorization.substring(7);

        FirebaseToken token;
        try {
            token = firebaseTokenService.verify(idToken);
        } catch (FirebaseAuthException exception) {
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        } catch (IllegalArgumentException exception) {
            if (!FormResponseRequestMatcher.INSTANCE.matches(request)) {
                throw exception;
            }
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        Optional<Role> role = authorizationService.getRole(token.getUid());
        if (role.isPresent() || FormResponseRequestMatcher.INSTANCE.matches(request)) {
            AuthenticatedUser principal = new AuthenticatedUser(token.getUid(), token.getEmail(), role.orElse(null));
            List<GrantedAuthority> authorities = role.isPresent()
                    ? List.of(new SimpleGrantedAuthority("ROLE_" + role.get().name())) : List.of();
            Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            try {
                authorizationService.registerDailyAccess(principal.firebaseUid());
            } catch (DataAccessException | TransactionException exception) {
                LOGGER.warn("Falha ao registrar acesso diário (tipo={})", exception.getClass().getSimpleName());
            }
        }

        filterChain.doFilter(request, response);
    }
}
