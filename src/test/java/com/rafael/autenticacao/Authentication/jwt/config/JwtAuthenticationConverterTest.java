package com.rafael.autenticacao.Authentication.jwt.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationConverterTest {

    private JwtAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SecurityConfigByJwt().jwtAuthenticationConverter();
    }

    @Test
    void shouldMapAuthoritiesClaimWithoutAddingAnotherPrefix() {
        Jwt jwt = jwtWithAuthorities(List.of("ROLE_USER", "ROLE_ADMIN"));

        var authentication = converter.convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN", "FACTOR_BEARER");
    }

    @Test
    void shouldUseSubjectAsAuthenticationName() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = jwt(userId.toString(), List.of("ROLE_USER"));

        var authentication = converter.convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo(userId.toString());
        assertThat(authentication.getPrincipal()).isInstanceOf(OAuth2AuthenticatedPrincipal.class);

        var principal = (OAuth2AuthenticatedPrincipal) authentication.getPrincipal();
        String subject = principal.getAttribute("sub");
        assertThat(subject).isEqualTo(userId.toString());
    }

    private Jwt jwtWithAuthorities(List<String> authorities) {
        return jwt(UUID.randomUUID().toString(), authorities);
    }

    private Jwt jwt(String subject, List<String> authorities) {
        Instant issuedAt = Instant.now();

        return Jwt.withTokenValue("signed.jwt.token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .claim("authorities", authorities)
                .build();
    }
}
