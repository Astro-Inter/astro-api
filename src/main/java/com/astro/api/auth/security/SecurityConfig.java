package com.astro.api.auth.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableMethodSecurity(proxyTargetClass = true)
public class SecurityConfig {

    private final FirebaseAuthenticationFilter firebaseAuthenticationFilter;

    public SecurityConfig(FirebaseAuthenticationFilter firebaseAuthenticationFilter) {
        this.firebaseAuthenticationFilter = firebaseAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {

        return http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                   session.sessionCreationPolicy(
                           SessionCreationPolicy.STATELESS
                   )
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/ws", "/ws/**").permitAll()
                .requestMatchers(HttpMethod.GET,
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()
                .requestMatchers(HttpMethod.POST,
                    "/verify-email",
                    "/activate",
                    "/verify-key",
                    "/add-manager-email",
                    "/register-workspace"
                ).permitAll()
                 .anyRequest().authenticated()
            )
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> {
                    if (FormResponseRequestMatcher.INSTANCE.matches(request)) {
                        ApiSecurityResponseWriter.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                                "Não autenticado", "Envie um token Firebase válido");
                        return;
                    }
                    if (request.getRequestURI().startsWith("/admin/nrs/")) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        return;
                    }
                    response.sendError(isUnauthorizedRoute(request.getRequestURI())
                            ? HttpServletResponse.SC_UNAUTHORIZED : HttpServletResponse.SC_FORBIDDEN);
                })
                .accessDeniedHandler((request, response, exception) -> {
                    if (FormResponseRequestMatcher.INSTANCE.matches(request)) {
                        ApiSecurityResponseWriter.write(request, response, HttpServletResponse.SC_FORBIDDEN,
                                "Não autorizado", "Você não tem permissão para executar esta operação");
                        return;
                    }
                    new AccessDeniedHandlerImpl().handle(request, response, exception);
                }))
            .addFilterBefore(
                firebaseAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            )
            .build();
    }

    private static boolean isUnauthorizedRoute(String requestUri) {
        return requestUri.startsWith("/admin/nrs/") || requestUri.equals("/events") || requestUri.startsWith("/events/")
                || requestUri.equals("/workspace") || requestUri.startsWith("/workspace/")
                || requestUri.equals("/units") || requestUri.startsWith("/units/")
                || requestUri.equals("/chats") || requestUri.startsWith("/chats/");
    }
}
