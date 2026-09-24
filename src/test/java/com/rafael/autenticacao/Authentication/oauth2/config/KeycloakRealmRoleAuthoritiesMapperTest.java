package com.rafael.autenticacao.Authentication.oauth2.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmRoleAuthoritiesMapperTest {

    private final KeycloakRealmRoleAuthoritiesMapper mapper =
            new KeycloakRealmRoleAuthoritiesMapper();

    @Test
    void shouldMapOnlyApplicationRealmRolesAndPreserveOriginalAuthorities() {
        var oidcAuthority = oidcAuthorityWithClaims(Map.of(
                "realm_access", Map.of(
                        "roles", List.of(
                                "USER",
                                "ADMIN",
                                "offline_access",
                                "uma_authorization",
                                "unknown-role"
                        )
                )
        ));
        var scopeAuthority = new SimpleGrantedAuthority("SCOPE_openid");

        var mappedAuthorities = mapper.mapAuthorities(List.of(
                oidcAuthority,
                scopeAuthority
        ));

        assertThat(mappedAuthorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "OIDC_USER",
                        "SCOPE_openid",
                        "ROLE_USER",
                        "ROLE_ADMIN"
                );
    }

    @Test
    void shouldPreserveOriginalAuthoritiesWhenRealmAccessIsAbsent() {
        var oidcAuthority = oidcAuthorityWithClaims(Map.of());

        var mappedAuthorities = mapper.mapAuthorities(List.of(oidcAuthority));

        assertThat(mappedAuthorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("OIDC_USER");
    }

    @Test
    void shouldIgnoreMalformedRealmRolesClaim() {
        var oidcAuthority = oidcAuthorityWithClaims(Map.of(
                "realm_access", Map.of("roles", "USER")
        ));

        var mappedAuthorities = mapper.mapAuthorities(List.of(oidcAuthority));

        assertThat(mappedAuthorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("OIDC_USER");
    }

    private OidcUserAuthority oidcAuthorityWithClaims(Map<String, Object> additionalClaims) {
        Instant issuedAt = Instant.now();
        var claims = new java.util.HashMap<String, Object>();
        claims.put("sub", "keycloak-subject");
        claims.putAll(additionalClaims);

        var idToken = new OidcIdToken(
                "id-token",
                issuedAt,
                issuedAt.plusSeconds(300),
                claims
        );

        return new OidcUserAuthority(idToken);
    }
}
