package com.rafael.autenticacao.Authentication.session.cors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCorsConfigTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    private final DefaultCorsProcessor corsProcessor = new DefaultCorsProcessor();

    private CorsConfigurationSource configurationSource;

    @BeforeEach
    void setUp() {
        configurationSource = new SessionCorsConfig()
                .corsConfigurationSource(ALLOWED_ORIGIN);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/auth/session/login",
            "/usuarios/6f607dac-f7d6-4e4d-86eb-318df78b5a7c"
    })
    void preflightShouldAllowConfiguredOrigin(String requestUri) throws Exception {
        MockHttpServletRequest request = preflightRequest(requestUri, ALLOWED_ORIGIN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean processed = corsProcessor.processRequest(
                corsConfiguration(request),
                request,
                response
        );

        assertThat(processed).isTrue();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo(ALLOWED_ORIGIN);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
                .isEqualTo("true");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
                .contains("POST");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .containsIgnoringCase("Content-Type")
                .containsIgnoringCase("X-XSRF-TOKEN");
    }

    @Test
    void preflightShouldRejectOriginThatIsNotConfigured() throws Exception {
        MockHttpServletRequest request = preflightRequest(
                "/auth/session/login",
                "https://origem-nao-permitida.example"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean processed = corsProcessor.processRequest(
                corsConfiguration(request),
                request,
                response
        );

        assertThat(processed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    private MockHttpServletRequest preflightRequest(String requestUri, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", requestUri);
        request.addHeader(HttpHeaders.ORIGIN, origin);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        request.addHeader(
                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                "Content-Type, X-XSRF-TOKEN"
        );
        return request;
    }

    private CorsConfiguration corsConfiguration(MockHttpServletRequest request) {
        CorsConfiguration configuration = configurationSource.getCorsConfiguration(request);

        assertThat(configuration)
                .as("CORS configuration should match %s", request.getRequestURI())
                .isNotNull();

        return configuration;
    }
}
