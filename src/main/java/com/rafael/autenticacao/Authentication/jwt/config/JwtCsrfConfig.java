package com.rafael.autenticacao.Authentication.jwt.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

@Configuration
public class JwtCsrfConfig {

    @Bean("jwtCsrfTokenRepository")
    public CsrfTokenRepository jwtCsrfTokenRepository(
            @Value("${app.security.jwt.refresh-cookie-secure}") boolean secure
    ) {
        var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();

        repository.setCookieName("JWT-XSRF-TOKEN");
        repository.setHeaderName("X-JWT-XSRF-TOKEN");
        repository.setCookiePath("/auth/jwt");
        repository.setCookieCustomizer(cookie -> cookie
                .secure(secure)
                .sameSite("Lax")
        );

        return repository;
    }
}
