package com.rafael.autenticacao.Authentication.jwt.refresh.domain;

import com.rafael.autenticacao.Usuario.Domain.Entidade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "jwt_refresh_token")
public class JwtRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Entidade usuario;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(
            name = "token_hash",
            nullable = false,
            unique = true,
            updatable = false,
            length = 64
    )
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected JwtRefreshToken() {
    }

    public JwtRefreshToken(
            Entidade usuario,
            UUID familyId,
            String tokenHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.usuario = Objects.requireNonNull(usuario, "Usuario is required");
        this.familyId = Objects.requireNonNull(familyId, "Token family is required");
        this.tokenHash = Objects.requireNonNull(tokenHash, "Token hash is required");
        this.createdAt = Objects.requireNonNull(createdAt, "Creation time is required");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Expiration time is required");

        if (tokenHash.length() != 64) {
            throw new IllegalArgumentException("Token hash must contain 64 characters");
        }

        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Expiration must be after creation");
        }
    }

    public boolean isExpired(Instant referenceTime) {
        Objects.requireNonNull(referenceTime, "Reference time is required");
        return !expiresAt.isAfter(referenceTime);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isActive(Instant referenceTime) {
        return !isUsed() && !isRevoked() && !isExpired(referenceTime);
    }

    public void markAsUsed(Instant usedAt) {
        Objects.requireNonNull(usedAt, "Usage time is required");

        if (this.usedAt != null) {
            throw new IllegalStateException("Refresh token has already been used");
        }

        this.usedAt = usedAt;
    }

    public void revoke(Instant revokedAt) {
        Objects.requireNonNull(revokedAt, "Revocation time is required");

        if (this.revokedAt == null) {
            this.revokedAt = revokedAt;
        }
    }

    public UUID getId() {
        return id;
    }

    public Entidade getUsuario() {
        return usuario;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
