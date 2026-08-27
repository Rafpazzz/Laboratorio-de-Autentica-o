package com.rafael.autenticacao.Authentication.jwt.config;

import com.rafael.autenticacao.Authentication.jwt.handler.JwtAccessDeniedHandler;
import com.rafael.autenticacao.Authentication.jwt.handler.JwtAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
public class SecurityConfigByJwt {

    @Bean
    @Order(2)
    public SecurityFilterChain jwtSecurityFilterChain(
            HttpSecurity httpSecurity,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            JwtAccessDeniedHandler accessDeniedHandler,
            @Qualifier("jwtCorsConfigurationSource")
            CorsConfigurationSource jwtCorsConfigurationSource,
            @Qualifier("jwtCsrfTokenRepository")
            CsrfTokenRepository jwtCsrfTokenRepository
    ) throws Exception {
        return httpSecurity
                .securityMatcher("/auth/jwt/**")
                .cors(cors -> cors.configurationSource(jwtCorsConfigurationSource))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(jwtCsrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth/jwt/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/jwt/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/jwt/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/jwt/admin").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/auth/jwt/csrf").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                )
                .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authoritiesConverter = new JwtGrantedAuthoritiesConverter();

        authoritiesConverter.setAuthoritiesClaimName("authorities");
        authoritiesConverter.setAuthorityPrefix("");

        var authenticationConverter = new JwtAuthenticationConverter();

        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        return authenticationConverter;
    }

}
