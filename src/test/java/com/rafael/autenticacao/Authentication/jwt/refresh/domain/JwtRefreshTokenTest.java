package com.rafael.autenticacao.Authentication.jwt.refresh.domain;

import com.rafael.autenticacao.Usuario.Domain.Entidade;
import com.rafael.autenticacao.Usuario.Domain.Role;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtRefreshTokenTest {

    private static final String TOKEN_HASH = "a".repeat(64);

    @Test
    void newTokenShouldBeActiveBeforeExpiration() {
        Instant createdAt = Instant.parse("2026-08-01T10:00:00Z");
        JwtRefreshToken refreshToken = refreshToken(
                createdAt,
                createdAt.plus(2, ChronoUnit.HOURS)
        );

        assertThat(refreshToken.isActive(createdAt.plus(1, ChronoUnit.HOURS))).isTrue();
        assertThat(refreshToken.isUsed()).isFalse();
        assertThat(refreshToken.isRevoked()).isFalse();
    }

    @Test
    void tokenShouldExpireAtExactExpirationTime() {
        Instant createdAt = Instant.parse("2026-08-01T10:00:00Z");
        Instant expiresAt = createdAt.plus(2, ChronoUnit.HOURS);
        JwtRefreshToken refreshToken = refreshToken(createdAt, expiresAt);

        assertThat(refreshToken.isExpired(expiresAt)).isTrue();
        assertThat(refreshToken.isActive(expiresAt)).isFalse();
    }

    @Test
    void usedTokenShouldBecomeInactiveAndShouldNotBeConsumedAgain() {
        Instant createdAt = Instant.parse("2026-08-01T10:00:00Z");
        Instant usedAt = createdAt.plus(15, ChronoUnit.MINUTES);
        JwtRefreshToken refreshToken = refreshToken(
                createdAt,
                createdAt.plus(2, ChronoUnit.HOURS)
        );

        refreshToken.markAsUsed(usedAt);

        assertThat(refreshToken.isUsed()).isTrue();
        assertThat(refreshToken.getUsedAt()).isEqualTo(usedAt);
        assertThat(refreshToken.isActive(usedAt)).isFalse();
        assertThatThrownBy(() -> refreshToken.markAsUsed(usedAt.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void revocationShouldBeIdempotentAndShouldPreserveOriginalTime() {
        Instant createdAt = Instant.parse("2026-08-01T10:00:00Z");
        Instant firstRevocation = createdAt.plus(30, ChronoUnit.MINUTES);
        JwtRefreshToken refreshToken = refreshToken(
                createdAt,
                createdAt.plus(2, ChronoUnit.HOURS)
        );

        refreshToken.revoke(firstRevocation);
        refreshToken.revoke(firstRevocation.plusSeconds(30));

        assertThat(refreshToken.isRevoked()).isTrue();
        assertThat(refreshToken.getRevokedAt()).isEqualTo(firstRevocation);
        assertThat(refreshToken.isActive(firstRevocation)).isFalse();
    }

    @Test
    void constructorShouldRejectInvalidHashAndExpiration() {
        Instant createdAt = Instant.parse("2026-08-01T10:00:00Z");

        assertThatThrownBy(() -> new JwtRefreshToken(
                usuario(),
                UUID.randomUUID(),
                "short-hash",
                createdAt,
                createdAt.plus(2, ChronoUnit.HOURS)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new JwtRefreshToken(
                usuario(),
                UUID.randomUUID(),
                TOKEN_HASH,
                createdAt,
                createdAt
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private JwtRefreshToken refreshToken(Instant createdAt, Instant expiresAt) {
        return new JwtRefreshToken(
                usuario(),
                UUID.randomUUID(),
                TOKEN_HASH,
                createdAt,
                expiresAt
        );
    }

    private Entidade usuario() {
        return new Entidade(
                UUID.randomUUID(),
                "Rafael",
                "rafael@email.com",
                "encoded-password",
                30,
                Role.USER
        );
    }
}
