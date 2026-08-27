package com.rafael.autenticacao.Authentication.jwt.refresh.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtRefreshTokenCodecTest {

    private JwtRefreshTokenCodec codec;

    @BeforeEach
    void setUp() {
        codec = new JwtRefreshTokenCodec();
    }

    @Test
    void generatedTokenShouldBeUrlSafeAndContain256Bits() {
        String token = codec.generateToken();

        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
    }

    @Test
    void eachGenerationShouldProduceAnotherToken() {
        var generatedTokens = new HashSet<String>();

        IntStream.range(0, 100)
                .mapToObj(ignored -> codec.generateToken())
                .forEach(generatedTokens::add);

        assertThat(generatedTokens).hasSize(100);
    }

    @Test
    void hashShouldUseSha256AndReturnLowercaseHexadecimal() {
        String hash = codec.hashToken("refresh-token");

        assertThat(hash)
                .isEqualTo("0eb17643d4e9261163783a420859c92c7d212fa9624106a12b510afbec266120")
                .matches("[0-9a-f]{64}");
    }

    @Test
    void hashShouldBeDeterministic() {
        String firstHash = codec.hashToken("same-token");
        String secondHash = codec.hashToken("same-token");

        assertThat(firstHash).isEqualTo(secondHash);
    }

    @Test
    void hashShouldRejectNullOrBlankToken() {
        assertThatThrownBy(() -> codec.hashToken(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.hashToken("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
