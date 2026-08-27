package com.rafael.autenticacao.Authentication.jwt.controller;

import com.rafael.autenticacao.Authentication.jwt.dto.JwtLoginResponseDTO;
import com.rafael.autenticacao.Authentication.jwt.refresh.cookie.JwtRefreshTokenCookieFactory;
import com.rafael.autenticacao.Authentication.jwt.refresh.service.JwtRefreshTokenService;
import com.rafael.autenticacao.Authentication.jwt.service.JwtTokenService;
import com.rafael.autenticacao.Authentication.shared.dto.LoginRequestDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/auth/jwt")
public class AuthByJwtController {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final JwtRefreshTokenService refreshTokenService;
    private final JwtRefreshTokenCookieFactory refreshTokenCookieFactory;
    private final CsrfTokenRepository csrfTokenRepository;

    public AuthByJwtController(AuthenticationManager authenticationManager, JwtTokenService jwtTokenService, JwtRefreshTokenService refreshTokenService, JwtRefreshTokenCookieFactory refreshTokenCookieFactory, @Qualifier("jwtCsrfTokenRepository") CsrfTokenRepository csrfTokenRepository) {

        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<JwtLoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequestDTO) {

        var authenticationRequest = UsernamePasswordAuthenticationToken
                                            .unauthenticated(loginRequestDTO.email(), loginRequestDTO.password());

        var authentication = authenticationManager.authenticate(authenticationRequest);

        Jwt accessToken = jwtTokenService.issueToken(authentication);

        var refreshToken = refreshTokenService.issue(authentication);
        var refreshCookie = refreshTokenCookieFactory.create(refreshToken);

        return tokenResponse(accessToken, refreshCookie);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = JwtRefreshTokenCookieFactory.COOKIE_NAME, required = false) String rawRefreshToken, HttpServletRequest request, HttpServletResponse response){
        refreshTokenService.revokeFamily(rawRefreshToken);

        csrfTokenRepository.saveToken(null, request, response);

        ResponseCookie clearedRefreshCookie = refreshTokenCookieFactory.clear();

        response.addHeader(HttpHeaders.SET_COOKIE, clearedRefreshCookie.toString());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtLoginResponseDTO> refresh(
            @CookieValue(
                    name = JwtRefreshTokenCookieFactory.COOKIE_NAME,
                    required = false
            )
            String rawRefreshToken
    ) {
        var rotation = refreshTokenService.rotate(rawRefreshToken);
        Jwt accessToken = jwtTokenService.issueToken(rotation.authentication());
        var refreshCookie = refreshTokenCookieFactory.create(rotation.issuedRefreshToken());

        return tokenResponse(accessToken, refreshCookie);
    }

    @GetMapping("/admin")
    public ResponseEntity<String> adminResource() {
        return ResponseEntity.ok("Acesso administrativo autorizado");
    }

    @GetMapping("/me")
    public ResponseEntity<String> authenticatedUser(JwtAuthenticationToken authentication) {
        return ResponseEntity.ok(authentication.getName());
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }

    private ResponseEntity<JwtLoginResponseDTO> tokenResponse(
            Jwt accessToken,
            ResponseCookie refreshCookie
    ) {
        long expiresIn = Duration.between(
                accessToken.getIssuedAt(),
                accessToken.getExpiresAt()
        ).toSeconds();

        var response = new JwtLoginResponseDTO(
                accessToken.getTokenValue(),
                "Bearer",
                expiresIn
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(response);
    }

}
