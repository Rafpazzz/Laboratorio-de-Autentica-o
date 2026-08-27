package com.rafael.autenticacao.Authentication.jwt.service;

import com.rafael.autenticacao.Authentication.jwt.config.JwtSecurityConstants;
import com.rafael.autenticacao.Authentication.shared.userdetails.UsuarioDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

//monta um jwt para um usuario que ja teve um email e senha validos
@Service
public class JwtTokenService {

    private static final Duration ACCESS_TOKEN_TTL=Duration.ofMinutes(15);


    private final JwtEncoder jwtEncoder;

    public JwtTokenService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public Jwt issueToken(Authentication authentication) {
        Instant issuedAt = Instant.now();
        UsuarioDetails usuario = (UsuarioDetails) authentication.getPrincipal();

        List<String> authorities = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtSecurityConstants.ISSUER)
                .subject(usuario.getId().toString())
                .audience(List.of(JwtSecurityConstants.AUDIENCE))
                .claim("email", usuario.getUsername())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ACCESS_TOKEN_TTL))
                .id(UUID.randomUUID().toString())
                .claim("authorities",authorities)
                .build();

        JwsHeader header = JwsHeader
                .with(SignatureAlgorithm.RS256)
                .type("JWT")
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header,claims));
    }
}