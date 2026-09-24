package com.rafael.autenticacao.Authentication.saml.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.saml2.provider.service.authentication.Saml2ResponseAssertion;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SamlAuthoritiesMapperTest {

    private final SamlAuthoritiesMapper authoritiesMapper = new SamlAuthoritiesMapper();

    @Test
    void shouldMapOnlyApplicationRoles() {
        var assertion = assertionWithAttributes(Map.of(
                "Role", List.of(
                        "USER",
                        "admin",
                        "offline_access",
                        "unknown-role"
                )
        ));

        var authorities = authoritiesMapper.map(assertion);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void shouldIgnoreMissingRoleAttribute() {
        var assertion = assertionWithAttributes(Map.of(
                "email", List.of("rafael@email.com")
        ));

        var authorities = authoritiesMapper.map(assertion);

        assertThat(authorities).isEmpty();
    }

    @Test
    void shouldRemoveDuplicatedRoles() {
        var assertion = assertionWithAttributes(Map.of(
                "Role", List.of("USER", "user", "USER")
        ));

        var authorities = authoritiesMapper.map(assertion);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    private Saml2ResponseAssertion assertionWithAttributes(
            Map<String, List<Object>> attributes
    ) {
        return Saml2ResponseAssertion
                .withResponseValue("encoded-saml-response")
                .nameId("rafael")
                .attributes(attributes)
                .build();
    }
}
