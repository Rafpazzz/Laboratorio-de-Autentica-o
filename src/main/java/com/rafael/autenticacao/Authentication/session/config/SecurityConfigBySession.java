package com.rafael.autenticacao.Authentication.session.config;

import com.rafael.autenticacao.Authentication.session.handler.SessionAccessDeniedHandler;
import com.rafael.autenticacao.Authentication.session.handler.SessionAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfigBySession {

    @Bean
    @Order(1)
    public SecurityFilterChain sessionSecurityFilterChain(
            HttpSecurity httpSecurity,
            SessionAuthenticationEntryPoint sessionAuthenticationEntryPoint,
            SessionAccessDeniedHandler sessionAccessDeniedHandler,
            @Qualifier("sessionCorsConfigurationSource")
                    CorsConfigurationSource sessionCorsConfigurationSource,
            @Qualifier("sessionCsrfTokenRepository")
            CsrfTokenRepository sessionCsrfTokenRepository
    ) throws Exception {
        return httpSecurity
                .securityMatcher("/auth/session/**", "/usuarios/**")
                .cors(cors -> cors.configurationSource(sessionCorsConfigurationSource))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(sessionCsrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                )
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(sessionSecurityContextRepository())
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(sessionAuthenticationEntryPoint)
                        .accessDeniedHandler(sessionAccessDeniedHandler)

                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth/session/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/session/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/session/logout").authenticated()
                        .requestMatchers("/usuarios/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/auth/session/csrf").permitAll()
                        .anyRequest().authenticated()
                )
                .build();
    }

    @Bean
    public SecurityContextRepository sessionSecurityContextRepository() {
        var repository = new HttpSessionSecurityContextRepository();
        repository.setSpringSecurityContextKey("SPRING_SECURITY_CONTEXT_SESSION");

        return repository;
    }

    @Bean("sessionCsrfTokenRepository")
    public CsrfTokenRepository sessionCsrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(
            @Qualifier("sessionCsrfTokenRepository") CsrfTokenRepository csrfTokenRepository
    ) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)
        ));
    }
}
