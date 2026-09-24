package com.rafael.autenticacao.Authentication.oauth2.config;

import com.rafael.autenticacao.Authentication.oauth2.handler.OAuth2AccessDeniedHandler;
import com.rafael.autenticacao.Authentication.oauth2.handler.OAuth2AuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfigByOAuth2 {

    @Bean
    @Order(3)
    public SecurityFilterChain oauth2SecurityFilterChain(
            HttpSecurity httpSecurity,
            KeycloakRealmRoleAuthoritiesMapper authoritiesMapper,
            OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler,
            OAuth2AuthenticationEntryPoint authenticationEntryPoint,
            OAuth2AccessDeniedHandler accessDeniedHandler,
            @Qualifier("oauth2SecurityContextRepository")
            SecurityContextRepository securityContextRepository,
            @Qualifier("oauth2CorsConfigurationSource")
            CorsConfigurationSource corsConfigurationSource,
            @Value("${app.security.oauth2.frontend-success-url}") String frontendSuccessUrl
    ) throws Exception {
        return httpSecurity
                .securityMatcher("/auth/oauth2/**")
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/auth/oauth2/authorization/**",
                                "/auth/oauth2/callback/**"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/auth/oauth2/csrf",
                                "/auth/oauth2/logged-out"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint
                                .baseUri("/auth/oauth2/authorization")
                        )
                        .redirectionEndpoint(endpoint -> endpoint
                                .baseUri("/auth/oauth2/callback/*")
                        )
                        .userInfoEndpoint(userInfo -> userInfo
                                .userAuthoritiesMapper(authoritiesMapper)
                        )
                        .defaultSuccessUrl(frontendSuccessUrl, true)
                )
                .logout(logout -> logout
                        .logoutUrl("/auth/oauth2/logout")
                        .logoutSuccessHandler(logoutSuccessHandler)
                        .clearAuthentication(true)
                        .invalidateHttpSession(false)
                )
                .build();
    }

    @Bean
    public OAuth2AuthenticationEntryPoint oauth2AuthenticationEntryPoint(
            ObjectMapper objectMapper
    ) {
        return new OAuth2AuthenticationEntryPoint(objectMapper);
    }

    @Bean
    public OAuth2AccessDeniedHandler oauth2AccessDeniedHandler(ObjectMapper objectMapper) {
        return new OAuth2AccessDeniedHandler(objectMapper);
    }

    @Bean("oauth2SecurityContextRepository")
    public SecurityContextRepository oauth2SecurityContextRepository() {
        var repository = new HttpSessionSecurityContextRepository();
        repository.setSpringSecurityContextKey("SPRING_SECURITY_CONTEXT_OAUTH2");

        return repository;
    }

    @Bean
    public OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler(
            ClientRegistrationRepository clientRegistrationRepository
    ) {
        var logoutSuccessHandler = new OidcClientInitiatedLogoutSuccessHandler(
                clientRegistrationRepository
        );

        logoutSuccessHandler.setPostLogoutRedirectUri(
                "{baseUrl}/auth/oauth2/logged-out"
        );

        return logoutSuccessHandler;
    }
}
