package com.rafael.autenticacao.Authentication.saml.config;

import com.rafael.autenticacao.Usuario.Domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.saml2.provider.service.authentication.Saml2ResponseAssertionAccessor;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class SamlAuthoritiesMapper {

    private static final String ROLE_ATTRIBUTE = "Role";

    private static final Set<String> ALLOWED_ROLES = Arrays.stream(Role.values())
            .map(Role::name)
            .collect(Collectors.toUnmodifiableSet());

    public Collection<GrantedAuthority> map(Saml2ResponseAssertionAccessor assertion) {
        var roles = assertion.<Object>getAttribute(ROLE_ATTRIBUTE);

        if (roles == null) {
            return Set.of();
        }

        return roles.stream()
                .map(Object::toString)
                .map(role -> role.toUpperCase(Locale.ROOT))
                .filter(ALLOWED_ROLES::contains)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toUnmodifiableSet());
    }
}
