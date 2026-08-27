package com.rafael.autenticacao.Authentication.jwt.refresh.service;

import com.rafael.autenticacao.Authentication.jwt.refresh.crypto.JwtRefreshTokenCodec;
import com.rafael.autenticacao.Authentication.jwt.refresh.domain.JwtRefreshToken;
import com.rafael.autenticacao.Authentication.jwt.refresh.exception.InvalidRefreshTokenException;
import com.rafael.autenticacao.Authentication.jwt.refresh.exception.RefreshTokenReuseException;
import com.rafael.autenticacao.Authentication.jwt.refresh.repository.JwtRefreshTokenRepository;
import com.rafael.autenticacao.Authentication.shared.userdetails.UsuarioDetails;
import com.rafael.autenticacao.Usuario.Domain.Entidade;
import com.rafael.autenticacao.Usuario.Domain.Role;
import com.rafael.autenticacao.Usuario.Repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtRefreshTokenServiceTest {

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofHours(2);
    private static final String RAW_TOKEN = "raw-refresh-token";
    private static final String TOKEN_HASH = "a".repeat(64);
    private static final String NEXT_RAW_TOKEN = "next-raw-refresh-token";
    private static final String NEXT_TOKEN_HASH = "b".repeat(64);

    @Mock
    private JwtRefreshTokenRepository refreshTokenRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private JwtRefreshTokenCodec tokenCodec;

    private JwtRefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new JwtRefreshTokenService(
                refreshTokenRepository,
                usuarioRepository,
                tokenCodec,
                REFRESH_TOKEN_TTL
        );
    }

    @Test
    void shouldIssueRefreshTokenAndPersistOnlyItsHash() {
        UUID userId = UUID.randomUUID();
        Entidade user = user(userId);
        Authentication authentication = authenticatedUser(user);
        when(usuarioRepository.getReferenceById(userId)).thenReturn(user);
        when(tokenCodec.generateToken()).thenReturn(RAW_TOKEN);
        when(tokenCodec.hashToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        Instant beforeIssuing = Instant.now();

        IssuedRefreshToken issuedToken = refreshTokenService.issue(authentication);

        Instant afterIssuing = Instant.now();
        ArgumentCaptor<JwtRefreshToken> tokenCaptor = ArgumentCaptor.forClass(JwtRefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        JwtRefreshToken persistedToken = tokenCaptor.getValue();

        assertThat(issuedToken.tokenValue()).isEqualTo(RAW_TOKEN);
        assertThat(issuedToken.expiresAt()).isEqualTo(persistedToken.getExpiresAt());
        assertThat(persistedToken.getUsuario()).isSameAs(user);
        assertThat(persistedToken.getTokenHash()).isEqualTo(TOKEN_HASH);
        assertThat(persistedToken.getTokenHash()).isNotEqualTo(RAW_TOKEN);
        assertThat(persistedToken.getFamilyId()).isNotNull();
        assertThat(persistedToken.getCreatedAt()).isBetween(beforeIssuing, afterIssuing);
        assertThat(Duration.between(persistedToken.getCreatedAt(), persistedToken.getExpiresAt()))
                .isEqualTo(REFRESH_TOKEN_TTL);
    }

    @Test
    void shouldRejectUnauthenticatedRequest() {
        Authentication authentication = UsernamePasswordAuthenticationToken.unauthenticated(
                "rafael@email.com",
                "password"
        );

        assertThatThrownBy(() -> refreshTokenService.issue(authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario deve ser autenticado");

        verifyNoInteractions(usuarioRepository, tokenCodec, refreshTokenRepository);
    }

    @Test
    void shouldRejectAuthenticatedPrincipalWithUnexpectedType() {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "rafael@email.com",
                null,
                List.of()
        );

        assertThatThrownBy(() -> refreshTokenService.issue(authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario deve ser autenticado");

        verify(usuarioRepository, never()).getReferenceById(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(tokenCodec, refreshTokenRepository);
    }

    @Test
    void shouldRotateActiveTokenWithinTheSameFamilyAndAbsoluteExpiration() {
        Entidade user = user(UUID.randomUUID());
        UUID familyId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(REFRESH_TOKEN_TTL);
        JwtRefreshToken currentToken = refreshToken(
                user,
                familyId,
                Instant.now().minusSeconds(30),
                expiresAt
        );
        when(tokenCodec.hashToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHashForUpdate(TOKEN_HASH))
                .thenReturn(Optional.of(currentToken));
        when(tokenCodec.generateToken()).thenReturn(NEXT_RAW_TOKEN);
        when(tokenCodec.hashToken(NEXT_RAW_TOKEN)).thenReturn(NEXT_TOKEN_HASH);

        RotatedRefreshToken result = refreshTokenService.rotate(RAW_TOKEN);

        ArgumentCaptor<JwtRefreshToken> tokenCaptor = ArgumentCaptor.forClass(JwtRefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        JwtRefreshToken replacement = tokenCaptor.getValue();

        assertThat(currentToken.isUsed()).isTrue();
        assertThat(replacement.getUsuario()).isSameAs(user);
        assertThat(replacement.getFamilyId()).isEqualTo(familyId);
        assertThat(replacement.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(replacement.getTokenHash()).isEqualTo(NEXT_TOKEN_HASH);
        assertThat(replacement.isActive(Instant.now())).isTrue();
        assertThat(result.issuedRefreshToken().tokenValue()).isEqualTo(NEXT_RAW_TOKEN);
        assertThat(result.issuedRefreshToken().expiresAt()).isEqualTo(expiresAt);
        assertThat(result.authentication().isAuthenticated()).isTrue();
        assertThat(result.authentication().getPrincipal()).isInstanceOf(UsuarioDetails.class);
        assertThat(result.authentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
    }

    @Test
    void shouldRejectMissingRefreshTokenBeforeAccessingPersistence() {
        assertThatThrownBy(() -> refreshTokenService.rotate(" "))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Refresh token invalido ou expirado");

        verifyNoInteractions(tokenCodec, refreshTokenRepository, usuarioRepository);
    }

    @Test
    void shouldRejectRefreshTokenThatDoesNotExist() {
        when(tokenCodec.hashToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHashForUpdate(TOKEN_HASH))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate(RAW_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(tokenCodec, never()).generateToken();
    }

    @Test
    void shouldRejectExpiredRefreshTokenWithoutCreatingReplacement() {
        Entidade user = user(UUID.randomUUID());
        JwtRefreshToken expiredToken = refreshToken(
                user,
                UUID.randomUUID(),
                Instant.now().minus(Duration.ofHours(3)),
                Instant.now().minus(Duration.ofHours(1))
        );
        when(tokenCodec.hashToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHashForUpdate(TOKEN_HASH))
                .thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> refreshTokenService.rotate(RAW_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(tokenCodec, never()).generateToken();
    }

    @Test
    void shouldRejectRevokedRefreshTokenWithoutCreatingReplacement() {
        Entidade user = user(UUID.randomUUID());
        JwtRefreshToken revokedToken = refreshToken(
                user,
                UUID.randomUUID(),
                Instant.now().minusSeconds(30),
                Instant.now().plus(REFRESH_TOKEN_TTL)
        );
        revokedToken.revoke(Instant.now().minusSeconds(10));
        when(tokenCodec.hashToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHashForUpdate(TOKEN_HASH))
                .thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> refreshTokenService.rotate(RAW_TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(tokenCodec, never()).generateToken();
    }

    @Test
    void shouldRevokeFamilyAndReportReuseWhenRefreshTokenWasAlreadyUsed() {
        Entidade user = user(UUID.randomUUID());
        UUID familyId = UUID.randomUUID();
        JwtRefreshToken usedToken = refreshToken(
                user,
                familyId,
                Instant.now().minusSeconds(30),
                Instant.now().plus(REFRESH_TOKEN_TTL)
        );
        usedToken.markAsUsed(Instant.now().minusSeconds(10));
        when(tokenCodec.hashToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHashForUpdate(TOKEN_HASH))
                .thenReturn(Optional.of(usedToken));

        assertThatThrownBy(() -> refreshTokenService.rotate(RAW_TOKEN))
                .isInstanceOf(RefreshTokenReuseException.class);

        verify(refreshTokenRepository).revokeFamily(
                org.mockito.ArgumentMatchers.eq(familyId),
                org.mockito.ArgumentMatchers.any(Instant.class)
        );
        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(tokenCodec, never()).generateToken();
    }

    @Test
    void shouldRevokeFamilyAssociatedWithRefreshToken() {
        Entidade user = user(UUID.randomUUID());
        UUID familyId = UUID.randomUUID();
        JwtRefreshToken token = refreshToken(
                user,
                familyId,
                Instant.now().minusSeconds(30),
                Instant.now().plus(REFRESH_TOKEN_TTL)
        );
        when(tokenCodec.hashToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHashForUpdate(TOKEN_HASH))
                .thenReturn(Optional.of(token));
        Instant beforeRevocation = Instant.now();

        refreshTokenService.revokeFamily(RAW_TOKEN);

        Instant afterRevocation = Instant.now();
        ArgumentCaptor<Instant> revokedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(refreshTokenRepository).revokeFamily(
                org.mockito.ArgumentMatchers.eq(familyId),
                revokedAtCaptor.capture()
        );
        assertThat(revokedAtCaptor.getValue()).isBetween(beforeRevocation, afterRevocation);
    }

    @Test
    void shouldIgnoreMissingRefreshTokenDuringRevocation() {
        refreshTokenService.revokeFamily(" ");

        verifyNoInteractions(tokenCodec, refreshTokenRepository, usuarioRepository);
    }

    @Test
    void shouldNotRevealUnknownRefreshTokenDuringRevocation() {
        when(tokenCodec.hashToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHashForUpdate(TOKEN_HASH))
                .thenReturn(Optional.empty());

        refreshTokenService.revokeFamily(RAW_TOKEN);

        verify(refreshTokenRepository, never()).revokeFamily(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private static Authentication authenticatedUser(Entidade user) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new UsuarioDetails(user),
                null,
                List.of()
        );
    }

    private static JwtRefreshToken refreshToken(
            Entidade user,
            UUID familyId,
            Instant createdAt,
            Instant expiresAt
    ) {
        return new JwtRefreshToken(
                user,
                familyId,
                TOKEN_HASH,
                createdAt,
                expiresAt
        );
    }

    private static Entidade user(UUID id) {
        return new Entidade(
                id,
                "Rafael",
                "rafael@email.com",
                "encoded-password",
                25,
                Role.USER
        );
    }
}
