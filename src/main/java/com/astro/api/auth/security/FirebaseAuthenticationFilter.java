package com.astro.api.auth.security;

import com.astro.api.auth.service.AuthorizationService;
import com.astro.api.auth.service.FirebaseTokenService;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

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

        try {
            FirebaseToken token = firebaseTokenService.verify(idToken);

            Optional<Role> role = authorizationService.getRole(token.getUid());

            if (role.isPresent()) {
                AuthenticatedUser principal = new AuthenticatedUser(
                    token.getUid(),
                    token.getEmail(),
                    role.get()
                );

                GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role.get().name());

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(authority)
                );

                SecurityContextHolder
                        .getContext()
                        .setAuthentication(authentication);
            }
        } catch (FirebaseAuthException exception) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
