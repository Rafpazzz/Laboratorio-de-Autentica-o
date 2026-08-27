package com.rafael.autenticacao.Shared.Exception;

import com.rafael.autenticacao.Authentication.jwt.refresh.exception.InvalidRefreshTokenException;
import com.rafael.autenticacao.Authentication.jwt.refresh.exception.RefreshTokenReuseException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @ParameterizedTest
    @MethodSource("invalidRefreshTokenExceptions")
    void shouldReturnSameUnauthorizedResponseForInvalidAndReusedRefreshTokens(
            InvalidRefreshTokenException exception
    ) {
        var request = new MockHttpServletRequest("POST", "/auth/jwt/refresh");

        ResponseEntity<ProblemDetail> response = exceptionHandler
                .handleInvalidRefreshToken(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Unauthorized");
        assertThat(response.getBody().getDetail()).isEqualTo("Refresh token invalido ou expirado");
        assertThat(response.getBody().getInstance()).isEqualTo(URI.create("/auth/jwt/refresh"));
    }

    private static Stream<InvalidRefreshTokenException> invalidRefreshTokenExceptions() {
        return Stream.of(
                new InvalidRefreshTokenException(),
                new RefreshTokenReuseException()
        );
    }
}
