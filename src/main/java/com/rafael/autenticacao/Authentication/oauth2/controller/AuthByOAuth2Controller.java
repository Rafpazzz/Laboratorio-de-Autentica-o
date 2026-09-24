package com.rafael.autenticacao.Authentication.oauth2.controller;

import com.rafael.autenticacao.Authentication.oauth2.dto.OidcUserResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/auth/oauth2")
public class AuthByOAuth2Controller {

    private final String frontendLogoutUrl;

    public AuthByOAuth2Controller(
            @Value("${app.security.oauth2.frontend-logout-url}") String frontendLogoutUrl
    ) {
        this.frontendLogoutUrl = frontendLogoutUrl;
    }

    @GetMapping("/sobre")
    public ResponseEntity<OidcUserResponseDTO> authenticationDetails(
            @AuthenticationPrincipal OidcUser oidcUser
    ) {
        var authorities = oidcUser.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .sorted()
                .toList();

        var response = new OidcUserResponseDTO(
                oidcUser.getSubject(),
                oidcUser.getPreferredUsername(),
                oidcUser.getFullName(),
                oidcUser.getEmail(),
                oidcUser.getEmailVerified(),
                authorities
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }

    @GetMapping("/logged-out")
    public ResponseEntity<Void> loggedOut() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendLogoutUrl))
                .build();
    }
}
