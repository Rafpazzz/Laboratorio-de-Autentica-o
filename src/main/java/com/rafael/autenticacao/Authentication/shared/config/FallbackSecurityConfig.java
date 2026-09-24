package com.rafael.autenticacao.Authentication.shared.config;

import com.rafael.autenticacao.Authentication.shared.handler.FallbackSecurityHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class FallbackSecurityConfig {

    @Bean
    public FallbackSecurityHandler fallbackSecurityHandler(ObjectMapper objectMapper) {
        return new FallbackSecurityHandler(objectMapper);
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public SecurityFilterChain fallbackSecurityFilterChain(
            HttpSecurity httpSecurity,
            FallbackSecurityHandler securityHandler
    ) {
        return httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityHandler)
                        .accessDeniedHandler(securityHandler)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().denyAll()
                )
                .build();
    }
}
