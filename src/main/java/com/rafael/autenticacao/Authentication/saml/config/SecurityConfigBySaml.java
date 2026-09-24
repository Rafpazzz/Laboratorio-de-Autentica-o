package com.rafael.autenticacao.Authentication.saml.config;

import com.rafael.autenticacao.Authentication.saml.handler.SamlAccessDeniedHandler;
import com.rafael.autenticacao.Authentication.saml.handler.SamlAuthenticationEntryPoint;
import com.rafael.autenticacao.Authentication.saml.handler.SamlAuthenticationFailureHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml5AuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration(proxyBeanMethods = false)
public class SecurityConfigBySaml {

    @Bean
    public SamlAuthoritiesMapper samlAuthoritiesMapper() {
        return new SamlAuthoritiesMapper();
    }

    @Bean
    public SamlResponseAuthenticationConverter samlResponseAuthenticationConverter(
            SamlAuthoritiesMapper authoritiesMapper
    ) {
        return new SamlResponseAuthenticationConverter(authoritiesMapper);
    }

    @Bean
    public SamlAuthenticationFailureHandler samlAuthenticationFailureHandler(
            ObjectMapper objectMapper
    ) {
        return new SamlAuthenticationFailureHandler(objectMapper);
    }

    @Bean
    public SamlAccessDeniedHandler samlAccessDeniedHandler(ObjectMapper objectMapper) {
        return new SamlAccessDeniedHandler(objectMapper);
    }

    @Bean
    public SamlAuthenticationEntryPoint samlAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new SamlAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    @Order(4)
    public SecurityFilterChain samlSecurityFilterChain(
            HttpSecurity httpSecurity,
            SamlResponseAuthenticationConverter authenticationConverter,
            SamlAuthenticationFailureHandler authenticationFailureHandler,
            SamlAuthenticationEntryPoint authenticationEntryPoint,
            SamlAccessDeniedHandler accessDeniedHandler,
            @Qualifier("samlSecurityContextRepository")
            SecurityContextRepository samlSecurityContextRepository,
            @Qualifier("samlCorsConfigurationSource")
            CorsConfigurationSource corsConfigurationSource,
            @Value("${app.security.saml.frontend-success-url}") String frontendSuccessUrl
    ) {
        var authenticationProvider = new OpenSaml5AuthenticationProvider();
        authenticationProvider.setResponseAuthenticationConverter(authenticationConverter);

        return httpSecurity
                .securityMatcher(
                        "/auth/saml/**",
                        "/saml2/**",
                        "/login/saml2/**",
                        "/logout/saml2/**"
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .securityContext(context -> context
                        .securityContextRepository(samlSecurityContextRepository)
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/saml2/authenticate/**",
                                "/saml2/metadata/**",
                                "/saml2/service-provider-metadata/**",
                                "/login/saml2/sso/**",
                                "/logout/saml2/**"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/auth/saml/csrf",
                                "/auth/saml/logged-out"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .saml2Login(saml -> saml
                        .authenticationManager(authenticationProvider::authenticate)
                        .failureHandler(authenticationFailureHandler)
                        .defaultSuccessUrl(frontendSuccessUrl, true)
                )
                .logout(logout -> logout
                        .logoutUrl("/auth/saml/logout")
                        .logoutSuccessUrl("/auth/saml/logged-out")
                        .clearAuthentication(true)
                        .invalidateHttpSession(false)
                        .permitAll()
                )
                .saml2Logout(saml -> saml
                        .logoutUrl("/auth/saml/slo")
                )
                .saml2Metadata(withDefaults())
                .build();
    }

    @Bean
    public SecurityContextRepository samlSecurityContextRepository() {
        var repository = new HttpSessionSecurityContextRepository();
        repository.setSpringSecurityContextKey("SPRING_SECURITY_CONTEXT_SAML");

        return repository;
    }
}
