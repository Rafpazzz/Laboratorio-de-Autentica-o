package com.rafael.autenticacao.Authentication.jwt.config;

import com.rafael.autenticacao.Authentication.jwt.controller.AuthByJwtController;
import com.rafael.autenticacao.Authentication.jwt.cors.JwtCorsConfig;
import com.rafael.autenticacao.Authentication.jwt.handler.JwtAccessDeniedHandler;
import com.rafael.autenticacao.Authentication.jwt.handler.JwtAuthenticationEntryPoint;
import com.rafael.autenticacao.Authentication.jwt.refresh.cookie.JwtRefreshTokenCookieFactory;
import com.rafael.autenticacao.Authentication.jwt.refresh.service.IssuedRefreshToken;
import com.rafael.autenticacao.Authentication.jwt.refresh.service.JwtRefreshTokenService;
import com.rafael.autenticacao.Authentication.jwt.refresh.service.RotatedRefreshToken;
import com.rafael.autenticacao.Authentication.jwt.service.JwtTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthByJwtController.class)
@Import({
        SecurityConfigByJwt.class,
        JwtKeyConfig.class,
        JwtCsrfConfig.class,
        JwtCorsConfig.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
class JwtSecurityFilterChainTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private JwtRefreshTokenService refreshTokenService;

    @MockitoBean
    private JwtRefreshTokenCookieFactory refreshTokenCookieFactory;

    @Test
    void loginShouldAcceptCsrfCookieAndHeaderWithoutCreatingSession() throws Exception {
        var authentication = new TestingAuthenticationToken(
                "rafael@email.com",
                null,
                "ROLE_USER"
        );
        Jwt accessToken = responseToken();
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

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtTokenService.issueToken(authentication)).thenReturn(accessToken);
        when(refreshTokenService.issue(authentication)).thenReturn(refreshToken);
        when(refreshTokenCookieFactory.create(refreshToken)).thenReturn(refreshCookie);

        var csrfResult = mockMvc.perform(get("/auth/jwt/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-JWT-XSRF-TOKEN"))
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("JWT-XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();

        var result = mockMvc.perform(post("/auth/jwt/login")
                        .cookie(csrfCookie)
                        .header("X-JWT-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLoginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(accessToken.getTokenValue()))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("refresh_token=raw-refresh-token")
                ))
                .andReturn();

        assertThat(csrfResult.getRequest().getSession(false)).isNull();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void loginShouldRejectRequestWithoutCsrfTokenBeforeAuthenticating() throws Exception {
        mockMvc.perform(post("/auth/jwt/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLoginBody()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.instance").value("/auth/jwt/login"));

        verifyNoInteractions(
                authenticationManager,
                jwtTokenService,
                refreshTokenService,
                refreshTokenCookieFactory
        );
    }

    @Test
    void refreshShouldAcceptRefreshCookieAndCsrfWithoutAccessToken() throws Exception {
        var authentication = new TestingAuthenticationToken(
                "rafael@email.com",
                null,
                "ROLE_USER"
        );
        Jwt accessToken = responseToken();
        var issuedRefreshToken = new IssuedRefreshToken(
                "next-raw-refresh-token",
                Instant.now().plus(Duration.ofHours(2))
        );
        var rotation = new RotatedRefreshToken(issuedRefreshToken, authentication);
        ResponseCookie refreshCookie = ResponseCookie
                .from(JwtRefreshTokenCookieFactory.COOKIE_NAME, issuedRefreshToken.tokenValue())
                .httpOnly(true)
                .path("/auth/jwt")
                .sameSite("Lax")
                .maxAge(Duration.ofHours(2))
                .build();
        when(refreshTokenService.rotate("current-raw-refresh-token")).thenReturn(rotation);
        when(jwtTokenService.issueToken(authentication)).thenReturn(accessToken);
        when(refreshTokenCookieFactory.create(issuedRefreshToken)).thenReturn(refreshCookie);

        var csrfResult = mockMvc.perform(get("/auth/jwt/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("JWT-XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();

        var result = mockMvc.perform(post("/auth/jwt/refresh")
                        .cookie(
                                csrfCookie,
                                new Cookie(
                                        JwtRefreshTokenCookieFactory.COOKIE_NAME,
                                        "current-raw-refresh-token"
                                )
                        )
                        .header("X-JWT-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(accessToken.getTokenValue()))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("refresh_token=next-raw-refresh-token")
                ))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void refreshShouldRejectRequestWithoutCsrfBeforeRotatingToken() throws Exception {
        mockMvc.perform(post("/auth/jwt/refresh")
                        .cookie(new Cookie(
                                JwtRefreshTokenCookieFactory.COOKIE_NAME,
                                "current-raw-refresh-token"
                        )))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.instance").value("/auth/jwt/refresh"));

        verifyNoInteractions(
                authenticationManager,
                jwtTokenService,
                refreshTokenService,
                refreshTokenCookieFactory
        );
    }

    @Test
    void logoutShouldAcceptCsrfAndRefreshCookieWithoutAccessToken() throws Exception {
        ResponseCookie clearedRefreshCookie = ResponseCookie
                .from(JwtRefreshTokenCookieFactory.COOKIE_NAME, "")
                .httpOnly(true)
                .path("/auth/jwt")
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
        when(refreshTokenCookieFactory.clear()).thenReturn(clearedRefreshCookie);

        var csrfResult = mockMvc.perform(get("/auth/jwt/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("JWT-XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();

        var result = mockMvc.perform(post("/auth/jwt/logout")
                        .cookie(
                                csrfCookie,
                                new Cookie(
                                        JwtRefreshTokenCookieFactory.COOKIE_NAME,
                                        "raw-refresh-token"
                                )
                        )
                        .header("X-JWT-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(header -> header.contains("refresh_token=") && header.contains("Max-Age=0"))
                .anyMatch(header -> header.contains("JWT-XSRF-TOKEN=") && header.contains("Max-Age=0"));
        assertThat(result.getRequest().getSession(false)).isNull();
        verify(refreshTokenService).revokeFamily("raw-refresh-token");
        verifyNoInteractions(authenticationManager, jwtTokenService);
    }

    @Test
    void logoutShouldRejectRequestWithoutCsrfBeforeRevokingTokenFamily() throws Exception {
        mockMvc.perform(post("/auth/jwt/logout")
                        .cookie(new Cookie(
                                JwtRefreshTokenCookieFactory.COOKIE_NAME,
                                "raw-refresh-token"
                        )))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.instance").value("/auth/jwt/logout"));

        verifyNoInteractions(
                authenticationManager,
                jwtTokenService,
                refreshTokenService,
                refreshTokenCookieFactory
        );
    }

    @Test
    void preflightShouldAllowJwtOriginAndAuthorizationHeaderWithoutAccessToken() throws Exception {
        mockMvc.perform(options("/auth/jwt/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                HttpHeaders.AUTHORIZATION + ", X-JWT-XSRF-TOKEN"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        startsWith("GET")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        startsWith("Authorization")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("X-JWT-XSRF-TOKEN")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ));
    }

    @Test
    void protectedRouteShouldRejectRequestWithoutAccessToken() throws Exception {
        var result = mockMvc.perform(get("/auth/jwt/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Token de acesso ausente ou invalido"))
                .andExpect(jsonPath("$.instance").value("/auth/jwt/me"))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void protectedRouteShouldAcceptValidAccessTokenWithoutCreatingSession() throws Exception {
        String subject = UUID.randomUUID().toString();
        String accessToken = encodeToken(
                jwtEncoder,
                subject,
                Instant.now(),
                Instant.now().plus(15, ChronoUnit.MINUTES)
        );

        var result = mockMvc.perform(get("/auth/jwt/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().string(subject))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void protectedRouteShouldRejectTokenSignedWithAnotherKey() throws Exception {
        JwtKeyConfig anotherKeyConfig = new JwtKeyConfig();
        var anotherKeyPair = anotherKeyConfig.jwtKeyPair();
        JwtEncoder anotherEncoder = anotherKeyConfig.jwtEncoder(anotherKeyPair);
        String accessToken = encodeToken(
                anotherEncoder,
                UUID.randomUUID().toString(),
                Instant.now(),
                Instant.now().plus(15, ChronoUnit.MINUTES)
        );

        mockMvc.perform(get("/auth/jwt/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Token de acesso ausente ou invalido"))
                .andExpect(jsonPath("$.instance").value("/auth/jwt/me"));
    }

    @Test
    void protectedRouteShouldRejectExpiredAccessToken() throws Exception {
        Instant issuedAt = Instant.now().minus(20, ChronoUnit.MINUTES);
        String accessToken = encodeToken(
                jwtEncoder,
                UUID.randomUUID().toString(),
                issuedAt,
                issuedAt.plus(15, ChronoUnit.MINUTES)
        );

        mockMvc.perform(get("/auth/jwt/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Token de acesso ausente ou invalido"))
                .andExpect(jsonPath("$.instance").value("/auth/jwt/me"));
    }

    @Test
    void adminRouteShouldRejectRequestWithoutAccessToken() throws Exception {
        mockMvc.perform(get("/auth/jwt/admin"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Token de acesso ausente ou invalido"))
                .andExpect(jsonPath("$.instance").value("/auth/jwt/admin"));
    }

    @Test
    void adminRouteShouldRejectAuthenticatedUserWithoutAdminRole() throws Exception {
        String accessToken = validAccessTokenWithAuthorities("ROLE_USER");

        mockMvc.perform(get("/auth/jwt/admin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail")
                        .value("Permissão insuficiente para acessar esse recurso"))
                .andExpect(jsonPath("$.instance").value("/auth/jwt/admin"));
    }

    @Test
    void adminRouteShouldAcceptAccessTokenWithAdminRole() throws Exception {
        String accessToken = validAccessTokenWithAuthorities("ROLE_ADMIN");

        mockMvc.perform(get("/auth/jwt/admin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().string("Acesso administrativo autorizado"));
    }

    private String validAccessTokenWithAuthorities(String... authorities) {
        Instant issuedAt = Instant.now();

        return encodeToken(
                jwtEncoder,
                UUID.randomUUID().toString(),
                issuedAt,
                issuedAt.plus(15, ChronoUnit.MINUTES),
                List.of(authorities)
        );
    }

    private String encodeToken(
            JwtEncoder encoder,
            String subject,
            Instant issuedAt,
            Instant expiresAt
    ) {
        return encodeToken(
                encoder,
                subject,
                issuedAt,
                expiresAt,
                List.of("ROLE_USER")
        );
    }

    private String encodeToken(
            JwtEncoder encoder,
            String subject,
            Instant issuedAt,
            Instant expiresAt,
            List<String> authorities
    ) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtSecurityConstants.ISSUER)
                .subject(subject)
                .audience(List.of(JwtSecurityConstants.AUDIENCE))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("authorities", authorities)
                .build();

        JwsHeader header = JwsHeader
                .with(SignatureAlgorithm.RS256)
                .type("JWT")
                .build();

        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private Jwt responseToken() {
        Instant issuedAt = Instant.now();

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
