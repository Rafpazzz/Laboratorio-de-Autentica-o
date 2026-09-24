package com.rafael.autenticacao.Authentication.saml.controller;

import com.rafael.autenticacao.Authentication.saml.dto.SamlUserResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.saml2.provider.service.authentication.Saml2AssertionAuthentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2ResponseAssertionAccessor;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/auth/saml")
public class AuthBySamlController {

    private final String frontendLogoutUrl;

    public AuthBySamlController(
            @Value("${app.security.saml.frontend-logout-url}") String frontendLogoutUrl
    ) {
        this.frontendLogoutUrl = frontendLogoutUrl;
    }

    @GetMapping("/sobre")
    public ResponseEntity<SamlUserResponseDTO> authenticationDetails(
            Saml2AssertionAuthentication authentication
    ) {
        var authorities = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .sorted()
                .toList();

        var response = new SamlUserResponseDTO(
                authentication.getCredentials().getNameId(),
                attribute(authentication.getCredentials(), "username"),
                fullName(authentication.getCredentials()),
                attribute(authentication.getCredentials(), "email"),
                authentication.getRelyingPartyRegistrationId(),
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

    private String fullName(Saml2ResponseAssertionAccessor assertion) {
        String firstName = attribute(assertion, "firstName");
        String lastName = attribute(assertion, "lastName");

        return String.join(
                " ",
                firstName == null ? "" : firstName,
                lastName == null ? "" : lastName
        ).trim();
    }

    private String attribute(
            Saml2ResponseAssertionAccessor assertion,
            String attributeName
    ) {
        Object value = assertion.getFirstAttribute(attributeName);

        return value == null ? null : value.toString();
    }
}
