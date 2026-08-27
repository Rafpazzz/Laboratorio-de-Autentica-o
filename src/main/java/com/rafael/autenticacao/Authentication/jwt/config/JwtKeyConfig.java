package com.rafael.autenticacao.Authentication.jwt.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class JwtKeyConfig {

    @Bean
    public KeyPair jwtKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);

            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Não foi possivel gerar o par de chaves RSA", e);
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(@Qualifier("jwtKeyPair") KeyPair keyPair) {
        return NimbusJwtEncoder
                .withKeyPair(
                        (RSAPublicKey)keyPair.getPublic(),
                        (RSAPrivateKey) keyPair.getPrivate()
                )
                .algorithm(SignatureAlgorithm.RS256)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(@Qualifier("jwtKeyPair") KeyPair keyPair) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) keyPair.getPublic())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> issuerValidator= JwtValidators.createDefaultWithIssuer(JwtSecurityConstants.ISSUER);

        OAuth2TokenValidator<Jwt> audienceValidator = new JwtAudienceValidator(JwtSecurityConstants.AUDIENCE);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));

        return  decoder;
    }

}
