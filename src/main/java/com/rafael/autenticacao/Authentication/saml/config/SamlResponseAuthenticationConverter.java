package com.rafael.autenticacao.Authentication.saml.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml5AuthenticationProvider;
import org.springframework.security.saml2.provider.service.authentication.Saml2AssertionAuthentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;

import java.util.HashSet;

public class SamlResponseAuthenticationConverter implements Converter<
        OpenSaml5AuthenticationProvider.ResponseToken,
        Saml2Authentication
> {

    private final OpenSaml5AuthenticationProvider.ResponseAuthenticationConverter delegate =
            new OpenSaml5AuthenticationProvider.ResponseAuthenticationConverter();

    private final SamlAuthoritiesMapper authoritiesMapper;

    public SamlResponseAuthenticationConverter(SamlAuthoritiesMapper authoritiesMapper) {
        this.authoritiesMapper = authoritiesMapper;
    }

    @Override
    public Saml2Authentication convert(
            OpenSaml5AuthenticationProvider.ResponseToken responseToken
    ) {
        var authentication = (Saml2AssertionAuthentication) delegate.convert(responseToken);
        var authorities = new HashSet<>(
                authoritiesMapper.map(authentication.getCredentials())
        );

        authentication.getAuthorities().stream()
                .filter(FactorGrantedAuthority.class::isInstance)
                .forEach(authorities::add);

        return new Saml2AssertionAuthentication(
                authentication.getPrincipal(),
                authentication.getCredentials(),
                authorities,
                authentication.getRelyingPartyRegistrationId()
        );
    }
}
