package com.rafael.autenticacao.Authentication.oauth2.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
public class KeycloakRealmRoleAuthoritiesMapper implements GrantedAuthoritiesMapper {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";
    private static final Map<String, String> APPLICATION_ROLES = Map.of(
            "USER", "ROLE_USER",
            "ADMIN", "ROLE_ADMIN"
    );

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(
            Collection<? extends GrantedAuthority> authorities
    ) {
        Set<GrantedAuthority> mappedAuthorities = new LinkedHashSet<>(authorities);

        authorities.stream()
                .filter(OAuth2UserAuthority.class::isInstance)
                .map(OAuth2UserAuthority.class::cast)
                .map(OAuth2UserAuthority::getAttributes)
                .map(this::extractRealmRoles)
                .flatMap(Collection::stream)
                .map(APPLICATION_ROLES::get)
                .filter(mappedRole -> mappedRole != null)
                .map(SimpleGrantedAuthority::new)
                .forEach(mappedAuthorities::add);

        return mappedAuthorities;
    }

    private Set<String> extractRealmRoles(Map<String, Object> claims) {
        Object realmAccessClaim = claims.get(REALM_ACCESS_CLAIM);
        if (!(realmAccessClaim instanceof Map<?, ?> realmAccess)) {
            return Set.of();
        }

        Object rolesClaim = realmAccess.get(ROLES_CLAIM);
        if (!(rolesClaim instanceof Collection<?> roles)) {
            return Set.of();
        }

        Set<String> realmRoles = new LinkedHashSet<>();
        roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .forEach(realmRoles::add);

        return realmRoles;
    }
}
