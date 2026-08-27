package com.rafael.autenticacao.Authentication.jwt.refresh.crypto;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class JwtRefreshTokenCodec {
    private static final int TOKEN_SIZE_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateToken() {
        byte[] randomBayte = new byte[TOKEN_SIZE_BYTES];

        secureRandom.nextBytes(randomBayte);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBayte);
    }

    public String hashToken(String token) {
        if(token == null ||  token.isBlank()) {
            throw new IllegalArgumentException("Refrash Token nao pode vim nulo/vazio");
        }

        try{
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");

            byte[] hash = messageDigest.digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        }catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não esta valido", e);
        }
    }
}
