package com.rafael.autenticacao.Authentication.jwt.refresh.service;

import com.rafael.autenticacao.Authentication.jwt.refresh.crypto.JwtRefreshTokenCodec;
import com.rafael.autenticacao.Authentication.jwt.refresh.domain.JwtRefreshToken;
import com.rafael.autenticacao.Authentication.jwt.refresh.exception.InvalidRefreshTokenException;
import com.rafael.autenticacao.Authentication.jwt.refresh.exception.RefreshTokenReuseException;
import com.rafael.autenticacao.Authentication.jwt.refresh.repository.JwtRefreshTokenRepository;
import com.rafael.autenticacao.Authentication.shared.userdetails.UsuarioDetails;
import com.rafael.autenticacao.Usuario.Repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtRefreshTokenService {
    private final JwtRefreshTokenRepository refreshTokenRepository;
    private final UsuarioRepository usuarioRepository;
    private final JwtRefreshTokenCodec tokenCodec;
    private final Duration refreshTokenTtl;

    public JwtRefreshTokenService(
            JwtRefreshTokenRepository refreshTokenRepository,
            UsuarioRepository usuarioRepository,
            JwtRefreshTokenCodec tokenCodec,
            @Value("${app.security.jwt.refresh-token-ttl}") Duration refreshTokenTtl
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.usuarioRepository = usuarioRepository;
        this.tokenCodec = tokenCodec;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Transactional
    public IssuedRefreshToken issue(Authentication authentication) {
        if(!authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof UsuarioDetails usuarioDetails)) {
            throw new IllegalArgumentException("Usuario deve ser autenticado");
        }

        Instant createdAt = Instant.now();
        Instant expiresAt = createdAt.plus(refreshTokenTtl);

        String tokenValue = tokenCodec.generateToken();
        String tokenHash = tokenCodec.hashToken(tokenValue);

        var usuario = usuarioRepository.getReferenceById(usuarioDetails.getId());

        var refreshToken = new JwtRefreshToken(
                usuario,
                UUID.randomUUID(),
                tokenHash,
                createdAt,
                expiresAt
        );

        refreshTokenRepository.save(refreshToken);

        return new IssuedRefreshToken(tokenValue, expiresAt);
    }

    @Transactional(dontRollbackOn = RefreshTokenReuseException.class)
    public RotatedRefreshToken rotate(String rawRefreshToken) {
        if(rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        String tokenHash = tokenCodec.hashToken(rawRefreshToken);

        JwtRefreshToken currentToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash).orElseThrow(InvalidRefreshTokenException::new);

        Instant now = Instant.now();

        if(currentToken.isRevoked() || currentToken.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }

        if(currentToken.isUsed()) {
            refreshTokenRepository.revokeFamily(currentToken.getFamilyId(), now);

            throw new RefreshTokenReuseException();
        }

        currentToken.markAsUsed(now);

        String nextTokenValue = tokenCodec.generateToken();
        String nextTokenHash = tokenCodec.hashToken(nextTokenValue);

        var nextToken = new JwtRefreshToken(currentToken.getUsuario(),currentToken.getFamilyId(),nextTokenHash, now, currentToken.getExpiresAt());

        refreshTokenRepository.save(nextToken);

        UsuarioDetails userDetails = new UsuarioDetails(currentToken.getUsuario());

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(userDetails, null, userDetails.getAuthorities());

        var issuedRefreshToken = new IssuedRefreshToken(nextTokenValue, currentToken.getExpiresAt());

        return  new RotatedRefreshToken(issuedRefreshToken,authentication);
    }

    @Transactional
    public void revokeFamily(String rawRefreshToken) {
        if(rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String tokenHash = tokenCodec.hashToken(rawRefreshToken);

        refreshTokenRepository.findByTokenHashForUpdate(tokenHash).ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now()));
    }
}
