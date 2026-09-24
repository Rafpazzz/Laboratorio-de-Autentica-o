package com.rafael.autenticacao.Authentication.jwt.service;

import com.rafael.autenticacao.Authentication.jwt.config.JwtKeyConfig;
import com.rafael.autenticacao.Authentication.shared.userdetails.UsuarioDetails;
import com.rafael.autenticacao.Usuario.Domain.Entidade;
import com.rafael.autenticacao.Usuario.Domain.Role;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class JwtTokenServiceTest {

    private static JwtTokenService tokenService;
    private static JwtDecoder jwtDecoder;

    @BeforeAll
    static void setUp() {
        JwtKeyConfig keyConfig = new JwtKeyConfig();
        var keyPair = keyConfig.jwtKeyPair();

        tokenService = new JwtTokenService(keyConfig.jwtEncoder(keyPair));
        jwtDecoder = keyConfig.jwtDecoder(keyPair);
    }

    @Test
    void shouldIssueSignedTokenWithUserIdentityAndAudience() {
        UUID userId = UUID.randomUUID();
        var authentication = authenticatedUser(userId);

        Jwt issuedToken = tokenService.issueToken(authentication);
        Jwt decodedToken = jwtDecoder.decode(issuedToken.getTokenValue());

        assertThat(decodedToken.getSubject()).isEqualTo(userId.toString());
        assertThat(decodedToken.getAudience()).containsExactly("autenticacao-api");
        assertThat(decodedToken.getClaimAsString("email")).isEqualTo("rafael@email.com");
        assertThat(decodedToken.getClaimAsString("name")).isEqualTo("Rafael");
        assertThat(decodedToken.getIssuer().toString())
                .isEqualTo("https://autenticacao-lab.local");
    }

    @Test
    void shouldIssueTokenWithSortedAuthoritiesAndUniqueIdentifier() {
        var authentication = authenticatedUser(UUID.randomUUID());

        Jwt decodedToken = jwtDecoder.decode(
                tokenService.issueToken(authentication).getTokenValue()
        );

        assertThat(decodedToken.getClaimAsStringList("authorities"))
                .containsExactly("ROLE_USER", "SCOPE_READ");
        assertThatCode(() -> UUID.fromString(decodedToken.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldIssueRs256JwtValidForFifteenMinutes() {
        Instant beforeIssuing = Instant.now();

        Jwt issuedToken = tokenService.issueToken(authenticatedUser(UUID.randomUUID()));

        Instant afterIssuing = Instant.now();
        Jwt decodedToken = jwtDecoder.decode(issuedToken.getTokenValue());

        assertThat(decodedToken.getIssuedAt())
                .isBetween(beforeIssuing.truncatedTo(ChronoUnit.SECONDS), afterIssuing);
        assertThat(Duration.between(decodedToken.getIssuedAt(), decodedToken.getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(decodedToken.getHeaders())
                .containsEntry("alg", "RS256")
                .containsEntry("typ", "JWT");
    }

    private static UsernamePasswordAuthenticationToken authenticatedUser(UUID userId) {
        Entidade user = new Entidade(
                userId,
                "Rafael",
                "rafael@email.com",
                "encoded-password",
                25,
                Role.USER
        );
        UsuarioDetails principal = new UsuarioDetails(user);

        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(
                        new SimpleGrantedAuthority("SCOPE_READ"),
                        new SimpleGrantedAuthority("ROLE_USER")
                )
        );
    }
}
