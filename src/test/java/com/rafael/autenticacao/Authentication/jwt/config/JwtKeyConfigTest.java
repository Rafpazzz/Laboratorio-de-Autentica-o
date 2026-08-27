package com.rafael.autenticacao.Authentication.jwt.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtKeyConfigTest {

    private static JwtKeyConfig config;
    private static KeyPair keyPair;
    private static JwtEncoder jwtEncoder;
    private static JwtDecoder jwtDecoder;

    @BeforeAll
    static void setUp() {
        config = new JwtKeyConfig();
        keyPair = config.jwtKeyPair();
        jwtEncoder = config.jwtEncoder(keyPair);
        jwtDecoder = config.jwtDecoder(keyPair);
    }

    @Test
    void keyPairShouldUseRsaWith2048Bits() {
        assertThat(keyPair.getPublic()).isInstanceOf(RSAPublicKey.class);
        assertThat(keyPair.getPrivate()).isInstanceOf(RSAPrivateKey.class);

        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        assertThat(publicKey.getModulus().bitLength()).isEqualTo(2048);
        assertThat(privateKey.getModulus()).isEqualTo(publicKey.getModulus());
    }

    @Test
    void decoderShouldValidateTokenSignedByConfiguredEncoder() {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String token = encodeToken(
                jwtEncoder,
                issuedAt,
                JwtSecurityConstants.ISSUER,
                JwtSecurityConstants.AUDIENCE
        );

        var decodedToken = jwtDecoder.decode(token);

        assertThat(decodedToken.getSubject()).isEqualTo("rafael@email.com");
        assertThat(decodedToken.getIssuer().toString()).isEqualTo("https://autenticacao-lab.local");
        assertThat(decodedToken.getAudience()).containsExactly("autenticacao-api");
        assertThat(decodedToken.getIssuedAt()).isEqualTo(issuedAt);
        assertThat(decodedToken.getExpiresAt()).isEqualTo(issuedAt.plus(15, ChronoUnit.MINUTES));
        assertThat(decodedToken.getClaimAsStringList("roles")).containsExactly("ROLE_USER");
        assertThat(decodedToken.getHeaders()).containsEntry("alg", "RS256");
    }

    @Test
    void decoderShouldRejectTokenSignedByAnotherKeyPair() {
        KeyPair anotherKeyPair = config.jwtKeyPair();
        JwtEncoder anotherEncoder = config.jwtEncoder(anotherKeyPair);
        String token = encodeToken(
                anotherEncoder,
                Instant.now(),
                JwtSecurityConstants.ISSUER,
                JwtSecurityConstants.AUDIENCE
        );

        assertThatThrownBy(() -> jwtDecoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void decoderShouldRejectTokenFromUnexpectedIssuer() {
        String token = encodeToken(
                jwtEncoder,
                Instant.now(),
                "https://outro-emissor.local",
                JwtSecurityConstants.AUDIENCE
        );

        assertThatThrownBy(() -> jwtDecoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void decoderShouldRejectTokenForUnexpectedAudience() {
        String token = encodeToken(
                jwtEncoder,
                Instant.now(),
                JwtSecurityConstants.ISSUER,
                "outra-api"
        );

        assertThatThrownBy(() -> jwtDecoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    private String encodeToken(
            JwtEncoder encoder,
            Instant issuedAt,
            String issuer,
            String audience
    ) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject("rafael@email.com")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(15, ChronoUnit.MINUTES))
                .claim("roles", List.of("ROLE_USER"))
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();

        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
