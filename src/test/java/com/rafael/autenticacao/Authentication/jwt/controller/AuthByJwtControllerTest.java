package com.rafael.autenticacao.Authentication.jwt.controller;

import com.rafael.autenticacao.Authentication.jwt.refresh.cookie.JwtRefreshTokenCookieFactory;
import com.rafael.autenticacao.Authentication.jwt.refresh.exception.InvalidRefreshTokenException;
import com.rafael.autenticacao.Authentication.jwt.refresh.service.IssuedRefreshToken;
import com.rafael.autenticacao.Authentication.jwt.refresh.service.JwtRefreshTokenService;
import com.rafael.autenticacao.Authentication.jwt.refresh.service.RotatedRefreshToken;
import com.rafael.autenticacao.Authentication.jwt.service.JwtTokenService;
import com.rafael.autenticacao.Shared.Exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthByJwtControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private JwtRefreshTokenService refreshTokenService;

    @Mock
    private JwtRefreshTokenCookieFactory refreshTokenCookieFactory;

    @Mock
    private CsrfTokenRepository csrfTokenRepository;

    @Mock
    private Authentication authenticatedUser;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthByJwtController(
                        authenticationManager,
                        jwtTokenService,
                        refreshTokenService,
                        refreshTokenCookieFactory,
                        csrfTokenRepository
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void loginShouldAuthenticateCredentialsAndReturnAccessToken() throws Exception {
        Jwt accessToken = accessToken();
        var refreshToken = new IssuedRefreshToken(
                "raw-refresh-token",
                Instant.now().plus(Duration.ofHours(2))
        );
        ResponseCookie refreshCookie = ResponseCookie
                .from(JwtRefreshTokenCookieFactory.COOKIE_NAME, refreshToken.tokenValue())
                .httpOnly(true)
                .path("/auth/jwt")
                .sameSite("Lax")
                .maxAge(Duration.ofHours(2))
                .build();
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authenticatedUser);
        when(jwtTokenService.issueToken(authenticatedUser)).thenReturn(accessToken);
        when(refreshTokenService.issue(authenticatedUser)).thenReturn(refreshToken);
        when(refreshTokenCookieFactory.create(refreshToken)).thenReturn(refreshCookie);

        MvcResult result = mockMvc.perform(post("/auth/jwt/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLoginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("refresh_token=raw-refresh-token")
                ))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/auth/jwt")))
                .andReturn();

        ArgumentCaptor<UsernamePasswordAuthenticationToken> credentialsCaptor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(credentialsCaptor.capture());

        UsernamePasswordAuthenticationToken credentials = credentialsCaptor.getValue();
        assertThat(credentials.getPrincipal()).isEqualTo("rafael@email.com");
        assertThat(credentials.getCredentials()).isEqualTo("123456");
        assertThat(credentials.isAuthenticated()).isFalse();
        assertThat(result.getRequest().getSession(false)).isNull();

        var loginOrder = inOrder(
                authenticationManager,
                jwtTokenService,
                refreshTokenService,
                refreshTokenCookieFactory
        );
        loginOrder.verify(authenticationManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));
        loginOrder.verify(jwtTokenService).issueToken(authenticatedUser);
        loginOrder.verify(refreshTokenService).issue(authenticatedUser);
        loginOrder.verify(refreshTokenCookieFactory).create(refreshToken);
    }

    @Test
    void loginShouldReturnUnauthorizedAndNotIssueTokenWhenCredentialsAreInvalid() throws Exception {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/auth/jwt/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLoginBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.detail").value("Credenciais invalidas"))
                .andExpect(jsonPath("$.instance").value("/auth/jwt/login"));

        verifyNoInteractions(jwtTokenService, refreshTokenService, refreshTokenCookieFactory);
    }

    @Test
    void loginShouldReturnBadRequestAndNotAuthenticateInvalidBody() throws Exception {
        mockMvc.perform(post("/auth/jwt/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "email-invalido",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.detail").value("Requisicao possui campos invalidos"))
                .andExpect(jsonPath("$.fields[*].field", hasItems("email", "password")));

        verifyNoInteractions(
                authenticationManager,
                jwtTokenService,
                refreshTokenService,
                refreshTokenCookieFactory
        );
    }

    @Test
    void refreshShouldRotateCookieAndReturnNewAccessToken() throws Exception {
        Jwt accessToken = accessToken();
        var issuedRefreshToken = new IssuedRefreshToken(
                "next-raw-refresh-token",
                Instant.now().plus(Duration.ofHours(2))
        );
        var rotation = new RotatedRefreshToken(issuedRefreshToken, authenticatedUser);
        ResponseCookie refreshCookie = ResponseCookie
                .from(JwtRefreshTokenCookieFactory.COOKIE_NAME, issuedRefreshToken.tokenValue())
                .httpOnly(true)
                .path("/auth/jwt")
                .sameSite("Lax")
                .maxAge(Duration.ofHours(2))
                .build();
        when(refreshTokenService.rotate("current-raw-refresh-token")).thenReturn(rotation);
        when(jwtTokenService.issueToken(authenticatedUser)).thenReturn(accessToken);
        when(refreshTokenCookieFactory.create(issuedRefreshToken)).thenReturn(refreshCookie);

        mockMvc.perform(post("/auth/jwt/refresh")
                        .cookie(new Cookie(
                                JwtRefreshTokenCookieFactory.COOKIE_NAME,
                                "current-raw-refresh-token"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("refresh_token=next-raw-refresh-token")
                ))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")));

        verifyNoInteractions(authenticationManager);
        var refreshOrder = inOrder(
                refreshTokenService,
                jwtTokenService,
                refreshTokenCookieFactory
        );
        refreshOrder.verify(refreshTokenService).rotate("current-raw-refresh-token");
        refreshOrder.verify(jwtTokenService).issueToken(authenticatedUser);
        refreshOrder.verify(refreshTokenCookieFactory).create(issuedRefreshToken);
    }

    @Test
    void refreshShouldReturnUnauthorizedWhenCookieIsMissing() throws Exception {
        when(refreshTokenService.rotate(null)).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/auth/jwt/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.detail").value("Refresh token invalido ou expirado"))
                .andExpect(jsonPath("$.instance").value("/auth/jwt/refresh"));

        verify(refreshTokenService).rotate(null);
        verifyNoInteractions(authenticationManager, jwtTokenService, refreshTokenCookieFactory);
    }

    @Test
    void logoutShouldRevokeTokenFamilyAndClearRefreshAndCsrfCookies() throws Exception {
        ResponseCookie clearedRefreshCookie = ResponseCookie
                .from(JwtRefreshTokenCookieFactory.COOKIE_NAME, "")
                .httpOnly(true)
                .path("/auth/jwt")
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
        when(refreshTokenCookieFactory.clear()).thenReturn(clearedRefreshCookie);

        MvcResult result = mockMvc.perform(post("/auth/jwt/logout")
                        .cookie(new Cookie(
                                JwtRefreshTokenCookieFactory.COOKIE_NAME,
                                "raw-refresh-token"
                        )))
                .andExpect(status().isNoContent())
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("refresh_token=")
                ))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andReturn();

        verify(refreshTokenService).revokeFamily("raw-refresh-token");
        verify(csrfTokenRepository).saveToken(
                isNull(),
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
        verify(refreshTokenCookieFactory).clear();
        assertThat(result.getRequest().getSession(false)).isNull();
        verifyNoInteractions(authenticationManager, jwtTokenService);
    }

    private Jwt accessToken() {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        return Jwt.withTokenValue("signed.jwt.token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(15, ChronoUnit.MINUTES))
                .build();
    }

    private String validLoginBody() {
        return """
                {
                  "email": "rafael@email.com",
                  "password": "123456"
                }
                """;
    }
}
