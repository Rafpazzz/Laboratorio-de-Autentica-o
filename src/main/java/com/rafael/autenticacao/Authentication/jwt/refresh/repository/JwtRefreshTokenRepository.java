package com.rafael.autenticacao.Authentication.jwt.refresh.repository;

import com.rafael.autenticacao.Authentication.jwt.refresh.domain.JwtRefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;


public interface JwtRefreshTokenRepository extends JpaRepository<JwtRefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from JwtRefreshToken token
            where token.tokenHash = :tokenHash
            """)
    Optional<JwtRefreshToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            UPDATE JwtRefreshToken token
            SET token.revokedAt = :revokedAt
            WHERE token.familyId = :familyId
              AND token.revokedAt IS NULL
            """)
    int revokeFamily(
            @Param("familyId") UUID familyId,
            @Param("revokedAt") Instant revokedAt
    );
}
